/*
 * CampusNexo · nodo de salón (Arduino IDE, placa ESP32 DevKit).
 *
 * Mide el ambiente del salón y publica cada 10 s en MQTT:
 *   ucc/pasto/<bloque>/<piso>/<salon>/ambiente
 *   {"space_id":"421-A","temperature_c":19.8,"humidity_pct":55.0,"illuminance_lux":410,
 *    "lights_on":true,"devices_on":3}
 * y escucha órdenes del backend en
 *   ucc/pasto/<bloque>/<piso>/<salon>/equipos/comando
 *   {"command_id":"…","lights_off":true,"suspend_devices":true,"reason":"Salón vacío hace 6 min…"}
 *
 * Hardware (valores ilustrativos, calibrar en el salón):
 *   - DHT22 (temperatura y humedad) en GPIO 4.
 *   - Fotorresistencia (LDR) con divisor de 10 kΩ en GPIO 34: estima el nivel de luz.
 *   - Sensor de corriente ACS712-20A en el circuito de los computadores, en GPIO 35.
 *   - Relé 1 (luces) en GPIO 26 y relé 2 (regleta de los computadores) en GPIO 27.
 *   - LED de aviso en GPIO 2: parpadea 60 s antes de cortar la regleta de los computadores.
 *
 * Bibliotecas: PubSubClient (Nick O'Leary), ArduinoJson 7, DHT sensor library (Adafruit).
 * La cámara no pasa por esta placa: el conteo de personas lo publica el equipo de visión en el canal
 * "camara". Las credenciales van en secrets.h (no se versiona), con este formato:
 *   #define WIFI_SSID "…"
 *   #define WIFI_PASSWORD "…"
 *   #define MQTT_HOST "192.168.1.10"
 */
#include <WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include <DHT.h>
#include "secrets.h"

// ---- Salón que atiende esta placa ------------------------------------------------------------
const char* SPACE_ID = "421-A";
const char* BLOCK = "bloque_a";
const char* FLOOR = "piso_4";
const int MQTT_PORT = 1883;

// ---- Pines -----------------------------------------------------------------------------------
const int PIN_DHT = 4;
const int PIN_LDR = 34;
const int PIN_CURRENT = 35;
const int PIN_RELAY_LIGHTS = 26;
const int PIN_RELAY_DEVICES = 27;
const int PIN_WARNING_LED = 2;

// ---- Calibración (ilustrativa) ---------------------------------------------------------------
const float LUX_PER_ADC = 0.25f;            // lux por unidad del ADC de la LDR
const float LIGHTS_ON_LUX = 150.0f;         // por encima, las luces se consideran encendidas
const float ACS712_MV_PER_AMP = 100.0f;     // sensibilidad del ACS712-20A
const float AMPS_PER_COMPUTER = 0.45f;      // consumo típico de un equipo encendido a 120 V
const unsigned long PUBLISH_EVERY_MS = 10000;
const unsigned long DEVICE_WARNING_MS = 60000;

DHT dht(PIN_DHT, DHT22);
WiFiClient wifi;
PubSubClient mqtt(wifi);
char topicReading[96];
char topicCommand[96];
unsigned long lastPublish = 0;
unsigned long devicesCutAt = 0;  // 0 = no hay corte pendiente

float readLux() {
  return analogRead(PIN_LDR) * LUX_PER_ADC;
}

/** Corriente RMS del circuito de computadores (muestreo de 200 ms) y equipos encendidos estimados. */
int readDevicesOn() {
  const float zeroMv = 1650.0f;  // salida del ACS712 sin carga tras el divisor a 3,3 V
  double sumSquares = 0;
  int samples = 0;
  unsigned long start = millis();
  while (millis() - start < 200) {
    float mv = analogReadMilliVolts(PIN_CURRENT) - zeroMv;
    sumSquares += mv * mv;
    samples++;
  }
  float amps = sqrt(sumSquares / samples) / ACS712_MV_PER_AMP;
  return max(0, (int) lround(amps / AMPS_PER_COMPUTER));
}

void publishReading() {
  float temperature = dht.readTemperature();
  float humidity = dht.readHumidity();
  if (isnan(temperature) || isnan(humidity)) {
    Serial.println("DHT22 sin lectura; se omite este envío");
    return;
  }
  float lux = readLux();
  JsonDocument doc;
  doc["space_id"] = SPACE_ID;
  doc["temperature_c"] = round(temperature * 10) / 10.0;
  doc["humidity_pct"] = round(humidity * 10) / 10.0;
  doc["illuminance_lux"] = round(lux);
  doc["lights_on"] = digitalRead(PIN_RELAY_LIGHTS) == HIGH && lux >= LIGHTS_ON_LUX;
  doc["devices_on"] = readDevicesOn();
  char payload[192];
  size_t length = serializeJson(doc, payload);
  mqtt.publish(topicReading, (const uint8_t*) payload, length, false);
}

/** Orden del backend: apaga las luces de inmediato y corta los equipos después del aviso. */
void onCommand(char* topic, byte* payload, unsigned int length) {
  JsonDocument doc;
  if (deserializeJson(doc, payload, length)) {
    Serial.println("Orden ilegible; se ignora");
    return;
  }
  if (doc["lights_off"] | false) {
    digitalWrite(PIN_RELAY_LIGHTS, LOW);
  }
  if (doc["suspend_devices"] | false) {
    devicesCutAt = millis() + DEVICE_WARNING_MS;
  }
  Serial.printf("Orden %s: %s\n", (const char*) (doc["command_id"] | "?"), (const char*) (doc["reason"] | ""));
  publishReading();  // confirma el nuevo estado al gemelo
}

void connect() {
  while (WiFi.status() != WL_CONNECTED) {
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    delay(3000);
  }
  while (!mqtt.connected()) {
    String clientId = String("campusnexo-") + SPACE_ID;
    if (mqtt.connect(clientId.c_str())) {
      mqtt.subscribe(topicCommand, 1);
    } else {
      delay(2000);
    }
  }
}

void setup() {
  Serial.begin(115200);
  pinMode(PIN_RELAY_LIGHTS, OUTPUT);
  pinMode(PIN_RELAY_DEVICES, OUTPUT);
  pinMode(PIN_WARNING_LED, OUTPUT);
  digitalWrite(PIN_RELAY_LIGHTS, HIGH);   // relés normalmente cerrados: el salón arranca con energía
  digitalWrite(PIN_RELAY_DEVICES, HIGH);
  analogReadResolution(12);
  dht.begin();
  snprintf(topicReading, sizeof topicReading, "ucc/pasto/%s/%s/%s/ambiente", BLOCK, FLOOR, SPACE_ID);
  snprintf(topicCommand, sizeof topicCommand, "ucc/pasto/%s/%s/%s/equipos/comando", BLOCK, FLOOR, SPACE_ID);
  mqtt.setServer(MQTT_HOST, MQTT_PORT);
  mqtt.setCallback(onCommand);
}

void loop() {
  if (!mqtt.connected()) {
    connect();
  }
  mqtt.loop();
  unsigned long now = millis();
  if (devicesCutAt != 0) {
    digitalWrite(PIN_WARNING_LED, (now / 500) % 2);
    if ((long) (now - devicesCutAt) >= 0) {
      digitalWrite(PIN_RELAY_DEVICES, LOW);
      digitalWrite(PIN_WARNING_LED, LOW);
      devicesCutAt = 0;
      publishReading();
    }
  }
  if (now - lastPublish >= PUBLISH_EVERY_MS) {
    lastPublish = now;
    publishReading();
  }
}
