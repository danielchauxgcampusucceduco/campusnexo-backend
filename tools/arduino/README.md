# Nodo de salón con Arduino

`campusnexo_salon/campusnexo_salon.ino` es el firmware del nodo que va en cada salón. Se programa con
el IDE de Arduino sobre una placa ESP32 DevKit (núcleo "esp32" de Espressif).

| Qué mide o hace | Componente | Canal MQTT |
|---|---|---|
| Temperatura y humedad | DHT22 | `ucc/pasto/<bloque>/<piso>/<salón>/ambiente` |
| Nivel de luz, luces encendidas | Fotorresistencia + relé 1 | mismo mensaje (`illuminance_lux`, `lights_on`) |
| Computadores encendidos | ACS712 en el circuito de los equipos | mismo mensaje (`devices_on`) |
| Apagar luces y equipos | Relé 1, relé 2 y LED de aviso | escucha `.../equipos/comando` |

El conteo de personas no pasa por esta placa: lo publica el equipo de visión (YOLO + ByteTrack, ver
`edge/` del repositorio de IA) en el canal `camara` del mismo salón. El backend une los dos canales en el gemelo.

1. Instala en el IDE las bibliotecas PubSubClient, ArduinoJson 7 y DHT sensor library.
2. Crea `campusnexo_salon/secrets.h` con `WIFI_SSID`, `WIFI_PASSWORD` y `MQTT_HOST` (no lo subas al repositorio).
3. Ajusta `SPACE_ID`, `BLOCK` y `FLOOR` al salón, y calibra `LUX_PER_ADC` y `AMPS_PER_COMPUTER` con medidas reales.

Sin placa, la misma lectura se puede enviar por HTTP:

```sh
curl -X POST http://localhost:8080/api/v1/twin/environment -H 'Content-Type: application/json' \
  -d '{"space_id":"423-A","temperature_c":19.2,"humidity_pct":53,"illuminance_lux":390,"lights_on":true,"devices_on":0}'
```
