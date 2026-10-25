# Gemelo digital, visión edge, Arduino y control térmico

## El campus

Un solo campus: Universidad Cooperativa de Colombia, campus Pasto (Calle 18 # 47-150, Torobajo). Los salones salen del plano institucional **PLANOS_UCC_2025_-_B.dwg** (actualización de planimetría 2025-B, pisos 0 a 5): la migración `V9` carga los 107 espacios que se reconocen en ese plano, con el área medida sobre él, y `V10` agrega los salones piloto 301-A (sala de cómputo, 30 puestos) y 303-A (aula, 36 pupitres), que no figuran en ese plano y tienen medidas ilustrativas.

- Los salones con código en el plano lo conservan: `AULA 420 A` → `420-A`, `AULA No 402 B` → `402-B`, las salas de informática 1 a 5 → `308-A` a `312-A`, el auditorio Urcunina → `608-B`.
- Los espacios sin código reciben uno con su piso, bloque y nombre (`4A-SALA-DE-JUNTAS-GALERAS`); la biblioteca, la hemeroteca, la cafetería y el auditorio del bloque A tienen códigos cortos (`BIBLIOTECA-2B`, `HEMEROTECA-3B`, `CAFETERIA-1B`, `AUDITORIO-0A`).
- **Bloque A**: salones con pupitres, salas de estudio, auditorio, oficinas y logística. **Bloque B**: biblioteca, cafetería, laboratorios, salas de cómputo y el auditorio Urcunina. Los espacios sin letra en el plano se asignan a un bloque según ese criterio.

**Salones de clase**: todos tienen **36 pupitres** (6 filas × 6), dato confirmado del campus. **Equipo de clima** (`hvac_installed`): por supuesto del prototipo, que debe confirmar la planta física, lo tienen los salones, laboratorios, salas de cómputo y auditorios; los demás nunca se climatizan (`SIN_CLIMATIZACION`) ni acumulan ahorro. Para los demás espacios, el aforo, la cuadrícula y los computadores se **estiman** a partir de su tipo y su área (2 m² por equipo en una sala de cómputo…): son valores ilustrativos, no el inventario oficial. Las salas de informática `308-A` a `312-A` son del bloque A.

## Flujo

```text
Cámara + YOLO/ByteTrack (repo de IA: edge/)  Arduino del salón (tools/arduino)
   │ MQTT ucc/pasto/{bloque}/{piso}/{salón}/camara   │ MQTT ucc/pasto/{bloque}/{piso}/{salón}/ambiente
   ▼                                          ▼
MqttInbox (uno por canal): confirma al broker DESPUÉS de procesar; a lo sumo un mensaje en espera por salón
YoloMqttTelemetryInboundAdapter        ArduinoMqttEnvironmentInboundAdapter
POST /api/v1/twin/telemetry (SENSOR)   POST /api/v1/twin/environment (SENSOR)
   └──► ProcessSpaceTelemetryUseCase     └──► ProcessEnvironmentReadingUseCase     ◄── TwinControlUseCase
        ProcessCameraFrameService             ProcessEnvironmentReadingService         SpaceReevaluationService
          1. ReadingAdmission: fecha creíble o 400 sin tocar nada
          2. reservas y regiones de pupitre (Proxy en memoria) leídas antes del candado
          3. ¿más nuevo que el último frame del salón? si no, se ignora (reentrega MQTT)
          ├─ OccupancyKalmanDomainService     cámara + CO2 + Wi-Fi → ocupación (x, P)
          └─ StudyZoneRoi.evaluateDeskGhosting ray casting por pupitre + cronómetro de abandono
                          ▼ (los tres casos de uso comparten)
TwinWriter         candado por salón; cada intento en UNA transacción (UnitOfWork): lee → decide → guarda con
                   revisión → guarda la lectura; si otra instancia escribió antes, se recalcula (hasta 3 veces)
TwinDecisionEngine (sin lecturas ni efectos)
   ├─ SpaceStatusPolicy                 aforo, reserva, no-show, pop-up, mantenimiento
   ├─ ThermalInertiaMpcDomainService    modelo RC sobre la banda de confort de la familia climática
   ├─ HvacSettingChain                  sin equipo → mantenimiento → docente → compresor → familia → MPC
   ├─ RoomConditionPolicy               personas + luces + equipos → estado general del salón
   └─ IdleEquipmentShutdownPolicy       vacío > 5 min con energía encendida → orden al Arduino
TelemetryUseCase.ingest                 lectura (con el conteo crudo de la cámara) en la misma transacción;
                                        los observadores de alertas se notifican al confirmar
TwinPublisher
   ├─ HvacActuatorCommandPort           Esp32HvacActuatorAdapter (MQTT) o LoggingHvacActuatorAdapter
   ├─ RoomEquipmentCommandPort          ArduinoEquipmentCommandAdapter (MQTT) o LoggingEquipmentCommandAdapter
   └─ TwinChangeNotifierPort            StompTwinChangeNotifierAdapter (/topic/twin, /topic/traces)
```

Las órdenes llegan a los equipos reales solo con `MQTT_ENABLED=true` **y** `ACTUATORS_MODE=mqtt`; por defecto (`log`) van al log y al dashboard. Las decisiones de un mismo salón nunca se pisan: dentro de una instancia esperan su turno y entre instancias la columna `revision` de `space_twin_states` rechaza una escritura basada en un estado viejo, y la decisión se recalcula con el estado nuevo.

**Cada lectura se aplica una vez y en orden.** El gemelo guarda la hora de captura del último frame de la cámara y de la última lectura del Arduino. Una reentrega de MQTT (QoS 1) o un mensaje que llega tarde no cambian nada y se cuentan en `campusnexo_readings_discarded_total{reason="duplicada"|"fuera_de_orden"}`; la base de datos lo garantiza además con `UNIQUE (space_id, recorded_at)`. El cliente MQTT usa sesión persistente (`clean session = false`, `MQTT_INSTANCE_ID` estable) y confirmación manual: si el proceso cae entre recibir y procesar, el broker vuelve a entregar el mensaje. Si llega un frame nuevo de un salón mientras el anterior sigue esperando, el viejo se descarta (`reemplazada`): el atraso queda acotado al número de salones. Un mensaje mal formado o inválido se confirma y se cuenta (`rechazada`); una falla de infraestructura se reintenta 3 veces antes de contarse como `fallida`.

**Estado general del salón** (`RoomConditionPolicy`): `OCUPADO` o `SOBRECUPO` si hay personas; si está vacío, `LIBRE`, `LUCES_SIN_OCUPACION`, `EQUIPOS_SIN_USO` o `LUCES_Y_EQUIPOS_SIN_USO`; `MANTENIMIENTO` tiene prioridad. Si un salón lleva más de `EQUIPMENT_IDLE_SHUTDOWN` (5 min) vacío con luces o equipos encendidos, se envía una orden de apagado al Arduino una sola vez por periodo; el gemelo la da por cumplida hasta que la siguiente lectura del Arduino la confirma o la corrige.

**Pupitres y asientos.** Los puestos de cada salón salen de su cuadrícula (`SeatGrid`): una región por pupitre, dejando libre la franja del frente (tablero, docente, puerta). La sala general de la biblioteca usa en cambio ocho mesas dibujadas a mano. Las oficinas y la logística no tienen cuadrícula: solo cuentan personas.

Cada evento genera una `ArchitectureTrace` con los pasos `INBOUND_ADAPTER → INPUT_PORT → USE_CASE → DOMAIN_SERVICE → OUTPUT_PORT → OUTBOUND_ADAPTER`. Los nombres de cada paso salen de la clase misma (`step(layer, Clase.class, …)`), así que no pueden quedar desactualizados. El dashboard la recibe por WebSocket y la consulta en `GET /api/v1/twin/traces`. `HexagonalArchitectureTest` (ArchUnit) hace fallar el build si el dominio o la aplicación importan algo que no sea la JDK, si hay ciclos entre módulos, si un adaptador de entrada usa una implementación en vez de un puerto, si alguien fuera de los casos de uso del gemelo escribe el gemelo o si la simulación usa algo distinto de los puertos de entrada.

## Rutas REST

Todas exigen sesión (👁 VIEWER para leer); 📡 = clave de sensor, 🛠 = OPERATOR, 🔐 = ADMIN.

| Método | Ruta | Uso |
|---|---|---|
| `GET` | `/api/v1/twin/spaces` | Gemelo de los 107 espacios: estado, estado general, luces, equipos, pupitres, Kalman y MPC |
| `GET` | `/api/v1/twin/spaces/{salón}` | Un salón |
| `GET` | `/api/v1/twin/savings` | Ahorro estimado por salón |
| `GET` | `/api/v1/twin/traces?limit=20` | Últimas trazas de la arquitectura |
| `POST` 📡 | `/api/v1/twin/telemetry` | Frame de cámara por HTTP (mismo JSON que MQTT) |
| `POST` 📡 | `/api/v1/twin/environment` | Lectura del Arduino por HTTP (mismo JSON que MQTT) |
| `POST` 🛠 | `/api/v1/twin/spaces/{salón}/override`, `/popup`, `/maintenance` | Controles manuales |
| `GET` 🔐 | `/actuator/prometheus` | Contadores operativos: lecturas descartadas, conflictos de escritura, respaldos de IA, estado del circuito y latencia del frame al gemelo |

## Contrato MQTT de entrada

### Cámara

Tópico: `ucc/pasto/bloque_b/piso_2/{space_id}/camara` (suscripción `ucc/+/+/+/+/camara`; también se acepta el canal anterior `telemetria`). El `space_id` del tópico debe coincidir con el del mensaje.

```json
{
  "space_id": "BIBLIOTECA-2B",
  "timestamp": "2026-10-20T15:00:00Z",
  "camera_online": true,
  "detections": [
    { "track_id": 17, "class": "person", "confidence": 0.91, "bbox": [0.31, 0.40, 0.36, 0.52] },
    { "track_id": 18, "class": "laptop", "confidence": 0.78, "bbox": [0.12, 0.42, 0.18, 0.47] }
  ],
  "sensors": {
    "co2_ppm": 760, "temperature_c": 20.5, "outdoor_temperature_c": 14.0, "humidity_pct": 52,
    "illuminance_lux": 420, "wifi_devices": 40, "active_power_kw": 5.4
  }
}
```

- `bbox` usa el formato `xyxyn` de YOLO (normalizado de 0 a 1). Las clases aceptadas son `person`, `laptop`, `backpack` y `book`; las demás se ignoran.
- Son obligatorios `sensors.co2_ppm`, `sensors.temperature_c` y `sensors.humidity_pct`. Si falta `timestamp`, se usa la hora de llegada; si falta `outdoor_temperature_c`, el promedio del campus.
- **No se publican imágenes**: solo cajas y conteos agregados (minimización de datos, Ley 1581 de 2012). El desenfoque en el dashboard es una representación visual de ese principio.

### Arduino del salón

Tópico: `ucc/pasto/bloque_a/piso_2/{space_id}/ambiente` (suscripción `ucc/+/+/+/+/ambiente`). Firmware de referencia: `tools/arduino/campusnexo_salon` (DHT22, fotorresistencia, sensor de corriente ACS712 y dos relés).

```json
{ "space_id": "423-A", "temperature_c": 19.2, "humidity_pct": 53, "illuminance_lux": 390,
  "lights_on": true, "devices_on": 0, "co2_ppm": 640 }
```

Son obligatorios `temperature_c`, `humidity_pct` y `lights_on`. `co2_ppm` es opcional (solo salones con sensor de CO₂) y `devices_on` no puede superar los computadores del salón.

## Contratos MQTT de salida

`{bloque}` y `{piso}` salen del código del salón (`201-A` → `bloque_a`, `piso_2`).

**Clima (ESP32).** Tópico `ucc/pasto/{bloque}/{piso}/{space_id}/clima/comando`. QoS 1, mensaje retenido y solo cuando cambia la estrategia, el modo o (con el equipo encendido) el setpoint. `strategy` es la estrategia del gemelo; `mode` es lo que debe hacer el equipo: `CALEFACCION`, `ENFRIAMIENTO`, `DESHUMIDIFICACION` o `APAGADO`.

```json
{ "command_id": "…", "space_id": "421-A", "strategy": "PRE_CALENTAMIENTO_IA", "mode": "CALEFACCION",
  "compressor_on": true, "setpoint_c": 19.5, "issued_at": "2026-10-20T11:40:00Z",
  "reason": "Clase en 20 min y llegaría a 16.8 °C: se pre-calienta (34 min necesarios)" }
```

Los espacios sin equipo de clima no reciben órdenes.

El firmware del ESP32 es responsable de los enclavamientos eléctricos; el backend solo indica la estrategia deseada.

**Luces y equipos (Arduino).** Tópico `ucc/pasto/{bloque}/{piso}/{space_id}/equipos/comando`. QoS 1 y **sin** retener: una orden vieja no debe apagar un salón que volvió a usarse cuando la placa se reconecta.

```json
{ "command_id": "…", "space_id": "309-A", "lights_off": true, "suspend_devices": true,
  "issued_at": "2026-10-20T15:00:00Z", "reason": "Salón vacío hace 12 min con luces encendidas, 24/24 equipos encendidos" }
```

## Modelos

**Kalman (ocupación).** Usa un modelo de paseo aleatorio. La predicción es `x⁻ = x` y `P⁻ = P + Q·Δt`. Después corrige en secuencia con cada medición `z` de varianza `R`: `K = P⁻/(P⁻+R)`, `x = x⁻ + K(z − x⁻)`, `P = (1 − K)P⁻`. Las mediciones son:

- la cámara (R = 0,64);
- el CO₂ por balance de masa, `(CO₂ − CO₂ₑₓₜ)·A/1000`, con un error relativo del 35 %;
- el Wi-Fi, a 0,8 personas por dispositivo y con un error relativo del 30 %.

Si la cámara cae, el filtro sigue estimando con las otras dos señales y la varianza aumenta.

**MPC térmico.** Modelo RC de primer orden, `T(k+1) = Tₑₓₜ + (T(k) − Tₑₓₜ)·e^(−Δt/τ) + g·n·(50/A)·Δt/60`, con τ = 120 min y paso de 5 min. En cada frame simula la trayectoria sin climatizar y elige la estrategia más barata que mantiene la **banda de confort de la familia climática** del salón (andina 19–24 °C, la misma que usa la recomendación):

- `CORTE_INERCIA_TERMICA` si la trayectoria se mantiene en la banda hasta que termina la clase;
- `PRE_CALENTAMIENTO_IA` o `PRE_ENFRIAMIENTO_IA` si una clase próxima empezaría bajo o sobre la banda y el tiempo restante apenas alcanza para llevarlo a ella (en Pasto, el caso habitual es calentar en la mañana);
- `ECO_SETBACK_ENTRE_CLASES` entre clases;
- `BLOQUEO_AHORRO_VACIO` si el espacio está vacío y no hay clase próxima;
- `VENTILACION_CO2_ACTIVA` si el CO₂ supera el umbral;
- `CONFORT_ACTIVO` en el resto de los casos.

Cada propuesta declara su modo (calentar o enfriar). Después decide la **cadena de reglas HVAC** (`HvacSettingChain`): un espacio sin equipo queda `SIN_CLIMATIZACION`; el mantenimiento apaga de inmediato; el control del docente lleva el salón al centro de la banda; la protección del compresor retiene un cambio que acortaría su ciclo (10 min encendido, 5 min de reposo); la estrategia de la familia elige el modo con gente en el salón (la tropical deshumidifica sobre 70 % de humedad aunque la temperatura esté bien). El resultado (`HvacSetting`: estrategia, modo, setpoint, desde cuándo y por qué) es lo que se comanda al ESP32 y lo que la recomendación toma como estado real del equipo.

**Puestos.** El algoritmo de ray casting (regla par-impar) asigna cada objeto a su puesto según el centro de su caja. Una persona en el puesto lo deja `OCUPADO_VERIFICADO`. Pertenencias sin persona lo pasan a `ALERTA_ACAPARAMIENTO` y arranca el cronómetro; a los 15 minutos el puesto queda `LIBERADO_POR_ABANDONO`.

**Ahorro.** Solo en espacios con equipo de clima. Se compara contra una línea base declarada, la climatización al 100 % entre las 07:00 y las 21:00 (hora de Bogotá), con potencia nominal de 0,12 kW/m²: es un contrafactual de modelo, no una medición de contador. Fuera de ese horario, encender el equipo cuenta como consumo adicional. Las conversiones usan la tarifa del campus (COP/kWh) y un factor de emisión configurable (`EMISSION_FACTOR_KG_CO2_PER_KWH`).

Todos los parámetros están en `app.twin.*` y son **ilustrativos**: no son mediciones calibradas.

## Rol por zona y escenarios reconocidos

**Docente adelante, estudiantes al fondo.** La franja frontal del salón (`y < 0,30`, sin pupitres) es la zona del docente. En cada frame, una persona en esa franja cuenta como docente y una dentro de la región de un pupitre como estudiante; las de los pasillos entran en el conteo fusionado pero en ningún rol. Se reconoce por **dónde** está cada persona, nunca por quién es; quién es lo dice el check-in del titular en el kiosco.

**Escenarios.** Cada respuesta del gemelo (REST y STOMP) trae `escenarios`, reconocidos sobre el estado con un detector por escenario:

| Escenario | Se reconoce cuando |
|---|---|
| `CLASE_EN_CURSO` | Hay un docente en su zona y al menos un estudiante en su puesto |
| `CO2_ALTO` | CO₂ ≥ 1000 ppm o el clima ya ventila por CO₂ |
| `FALLO_CAMARA` | La cámara se reportó caída o lleva más de 2 min sin enviar (un salón sin cámara no falla) |
| `SOBRECUPO`, `NO_SHOW`, `SUBUTILIZADO`, `POPUP_PARCIALES` | El estado de reserva y aforo lo dice |
| `LUCES_ENCENDIDAS`, `EQUIPOS_ENCENDIDOS` | El salón vacío tiene luces o equipos encendidos |
| `OVERRIDE_DOCENTE`, `MANTENIMIENTO` | Hay un control manual activo |
| `ACAPARAMIENTO` | Un puesto tiene pertenencias sin persona |

El backend no simula nada. Cualquier fuente de datos usa las mismas rutas y su propia clave de dispositivo; si sus frames no vienen de una cámara real los marca con `emulated: true`, el gemelo lo guarda en `Sensing.emulated` y el tablero muestra la insignia "Emulado".

## Puestos de trabajo y CPU

Un puesto es la mesa, la silla y el computador de un asiento de la rejilla (Composite `PuestoDeTrabajo`); su código es la región de cámara de ese asiento (`301-A-P07`). El agente de cada PC envía un latido por minuto (`POST /api/v1/workstations/heartbeats`): con el salón vacío y la cámara en línea, un computador que sigue reportando levanta `CPU_ENCENDIDA_SIN_USUARIO`.

**Registro automático:** la cámara propone los puestos con el salón vacío (`/workstations/discovery`), cada PC muestra un patrón en su ranura y la cámara reporta dónde lo vio (`/workstations/pairing`), y el administrador revisa y confirma (`/admin/spaces/{id}/propuesta`). Con el inventario confirmado, `/workstations/audit` compara lo que se ve con lo registrado y levanta `ACTIVO_FALTANTE`.

## Probar con MQTT local

```sh
docker compose up --build -d          # PostgreSQL, MongoDB, Mosquitto y backend
# cámara real (repo de IA): python -m edge.calibrar --space-id 301-A --source 0
#                            python -m edge.yolo_edge_publisher --calibracion calibracion-301A.json --source 0 --mqtt localhost
# lectura del Arduino sin placa:
mosquitto_pub -t ucc/pasto/bloque_a/piso_4/423-A/ambiente \
  -m '{"temperature_c":19.2,"humidity_pct":53,"illuminance_lux":390,"lights_on":true,"devices_on":0}'
```
