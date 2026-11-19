# Gemelo digital, visión edge y disponibilidad

## El campus

Un solo campus: Universidad Cooperativa de Colombia, campus Pasto (Calle 18 # 47-150, Torobajo). Los salones salen del plano institucional **PLANOS_UCC_2025_-_B.dwg** (actualización de planimetría 2025-B, pisos 0 a 5): la migración `V9` carga los 107 espacios que se reconocen en ese plano, con el área medida sobre él, `V10` agrega los salones piloto 301-A (sala de cómputo, 30 puestos) y 303-A (aula, 36 pupitres), que no figuran en ese plano, y `V17` las dos salas de las esquinas de la hemeroteca: `HEMEROTECA-3A-ASESORIAS` (asesorías, aforo 6) y `HEMEROTECA-3A-ESTUDIO` (estudio privado, aforo 2). Sus medidas son ilustrativas.

- Los salones con código en el plano lo conservan: `AULA 420 A` → `420-A`, `AULA No 402 B` → `402-B`, las salas de informática 1 a 5 → `308-A` a `312-A`, el auditorio Urcunina → `608-B`.
- Los espacios sin código reciben uno con su piso, bloque y nombre (`4A-SALA-DE-JUNTAS-GALERAS`); la biblioteca, la hemeroteca, la cafetería y el auditorio del bloque A tienen códigos cortos (`BIBLIOTECA-2A`, `HEMEROTECA-3A`, `CAFETERIA-1A`, `AUDITORIO-0A`).
- **Bloque A**: salones con pupitres, salas de estudio, auditorio, oficinas y logística. **Bloque B**: biblioteca, cafetería, laboratorios, salas de cómputo y el auditorio Urcunina. Los espacios sin letra en el plano se asignan a un bloque según ese criterio.

**Hardware**: una **cámara de visión PoE** por espacio, cableada al portátil de borde (switch o inyector PoE), y el portátil en la red del campus por Ethernet. No hay sensores de ambiente ni relés: el sistema no mide clima, CO₂ ni consumo, y no apaga nada.

**Espacios seguidos**: solo los que puede usar un estudiante (`SpaceType.accesibleParaEstudiantes()`): aulas, salas de cómputo, laboratorios, biblioteca, salas de estudio, auditorios y cafetería. Las oficinas y la logística siguen en el catálogo y en el plano, pero no se monitorean.

**Salones de clase**: todos tienen **36 pupitres** (6 filas × 6), dato confirmado del campus. Para los demás espacios, el aforo, la cuadrícula y los computadores se **estiman** a partir de su tipo y su área (2 m² por equipo en una sala de cómputo…): son valores ilustrativos, no el inventario oficial. Las salas de informática `308-A` a `312-A` son del bloque A.

## Flujo

```text
Cámara PoE ──Ethernet──► portátil de borde: YOLO/ByteTrack (repo de IA: edge/) ──red del campus──►
   │ MQTT ucc/pasto/{bloque}/{piso}/{salón}/camara      o      POST /api/v1/twin/telemetry (SENSOR)
   ▼
MqttInbox: confirma al broker DESPUÉS de procesar; a lo sumo un mensaje en espera por salón
YoloMqttTelemetryInboundAdapter / TwinIngestionController
   └──► ProcessSpaceTelemetryUseCase                                   ◄── TwinControlUseCase
        ProcessCameraFrameService                                          SpaceReevaluationService
          1. ReadingAdmission: fecha creíble o 400 sin tocar nada
          2. reservas y regiones de pupitre (Proxy en memoria) leídas antes del candado
          3. ¿más nuevo que el último frame del salón? si no, se ignora (reentrega MQTT)
          ├─ OccupancyKalmanDomainService     cámara + Wi-Fi opcional → ocupación (x, P)
          └─ StudyZoneRoi.evaluateDeskGhosting ray casting por pupitre + cronómetro de abandono
                          ▼
TwinWriter         candado por salón; cada intento en UNA transacción (UnitOfWork): lee → decide → guarda con
                   revisión → guarda la lectura; si otra instancia escribió antes, se recalcula (hasta 3 veces)
TwinDecisionEngine (sin lecturas ni efectos)
   └─ SpaceStatusPolicy                 aforo, reserva, no-show, pop-up, mantenimiento
TelemetryUseCase.ingest                 lectura (con el conteo crudo de la cámara) en la misma transacción;
                                        los observadores de alertas se notifican al confirmar
TwinPublisher
   └─ TwinChangeNotifierPort × N        StompTwinChangeNotifierAdapter (/topic/twin, /topic/traces), historial MongoDB

TwinWatchdogScheduler (cada 30 s, TWIN_WATCHDOG_INTERVAL)
   ├─ cámaras que dejaron de enviar → FALLO_CAMARA
   └─ DisponibilidadService.vigilarHorario → HORARIO_SIN_USO
```

El gemelo no ordena nada a ningún equipo: solo informa. Las decisiones de un mismo salón nunca se pisan: dentro de una instancia esperan su turno y entre instancias la columna `revision` de `space_twin_states` rechaza una escritura basada en un estado viejo, y la decisión se recalcula con el estado nuevo.

**Cada lectura se aplica una vez y en orden.** El gemelo guarda la hora de captura del último frame de la cámara. Una reentrega de MQTT (QoS 1) o un mensaje que llega tarde no cambian nada y se cuentan en `campusnexo_readings_discarded_total{reason="duplicada"|"fuera_de_orden"}`; la base de datos lo garantiza además con `UNIQUE (space_id, recorded_at)`. El cliente MQTT usa sesión persistente (`clean session = false`, `MQTT_INSTANCE_ID` estable) y confirmación manual: si el proceso cae entre recibir y procesar, el broker vuelve a entregar el mensaje. Si llega un frame nuevo de un salón mientras el anterior sigue esperando, el viejo se descarta (`reemplazada`): el atraso queda acotado al número de salones. Un mensaje mal formado o inválido se confirma y se cuenta (`rechazada`); una falla de infraestructura se reintenta 3 veces antes de contarse como `fallida`.

**Uso del salón** (lo calcula el tablero a partir del gemelo): `MANTENIMIENTO` si está fuera de servicio, `SOBRECUPO` con más personas que puestos, `OCUPADO` con personas y `LIBRE` vacío.

**Pupitres y asientos.** Los puestos de cada salón salen de su cuadrícula (`SeatGrid`): una región por pupitre, dejando libre la franja del frente (tablero, docente, puerta). La sala general de la biblioteca usa en cambio ocho mesas dibujadas a mano. Las oficinas y la logística no tienen cuadrícula: solo cuentan personas.

Cada evento genera una `ArchitectureTrace` con los pasos `INBOUND_ADAPTER → INPUT_PORT → USE_CASE → DOMAIN_SERVICE → OUTPUT_PORT → OUTBOUND_ADAPTER`. Los nombres de cada paso salen de la clase misma (`step(layer, Clase.class, …)`), así que no pueden quedar desactualizados. El dashboard la recibe por WebSocket y la consulta en `GET /api/v1/twin/traces`. `HexagonalArchitectureTest` (ArchUnit) hace fallar el build si el dominio o la aplicación importan algo que no sea la JDK, si hay ciclos entre módulos, si un adaptador de entrada usa una implementación en vez de un puerto, si alguien fuera de los casos de uso del gemelo escribe el gemelo o si la simulación usa algo distinto de los puertos de entrada.

## Rutas REST

Todas exigen sesión (👁 VIEWER para leer); 📡 = clave de sensor, 🛠 = OPERATOR, 🔐 = ADMIN.

| Método | Ruta | Uso |
|---|---|---|
| `GET` | `/api/v1/twin/spaces` | Gemelo de los espacios: estado, ocupación, pupitres, docentes y estudiantes, Kalman y escenarios |
| `GET` | `/api/v1/twin/spaces/{salón}` | Un salón |
| `GET` | `/api/v1/disponibilidad?fecha=` | Franjas de una hora (7:00 a 22:00) de cada espacio de estudiantes, ver abajo |
| `GET` | `/api/v1/twin/traces?limit=20` | Últimas trazas de la arquitectura |
| `POST` 📡 | `/api/v1/twin/telemetry` | Frame de cámara por HTTP (mismo JSON que MQTT) |
| `POST` 🛠 | `/api/v1/twin/spaces/{salón}/popup`, `/maintenance` | Controles manuales |
| `GET` 🔐 | `/actuator/prometheus` | Contadores operativos: lecturas descartadas, conflictos de escritura, respaldos de IA, estado del circuito y latencia del frame al gemelo |

## Contrato MQTT de entrada

### Cámara

Tópico: `ucc/pasto/bloque_b/piso_2/{space_id}/camara` (suscripción `ucc/+/+/+/+/camara`; también se acepta el canal anterior `telemetria`). El `space_id` del tópico debe coincidir con el del mensaje.

```json
{
  "space_id": "BIBLIOTECA-2A",
  "timestamp": "2026-10-20T15:00:00Z",
  "camera_online": true,
  "detections": [
    { "track_id": 17, "class": "person", "confidence": 0.91, "bbox": [0.31, 0.40, 0.36, 0.52] },
    { "track_id": 18, "class": "laptop", "confidence": 0.78, "bbox": [0.12, 0.42, 0.18, 0.47] }
  ],
  "wifi_devices": 40
}
```

- `bbox` usa el formato `xyxyn` de YOLO (normalizado de 0 a 1). Las clases aceptadas son `person`, `laptop`, `backpack` y `book`; las demás se ignoran.
- Solo la cámara: `wifi_devices` es opcional (celulares conectados que reporte la red del campus). Si falta `timestamp`, se usa la hora de llegada. Un frame de un firmware anterior con `sensors` se acepta y ese bloque se ignora.
- **No se publican imágenes**: solo cajas y conteos agregados (minimización de datos, Ley 1581 de 2012). El desenfoque en el dashboard es una representación visual de ese principio.

## Modelos

**Kalman (ocupación).** Usa un modelo de paseo aleatorio. La predicción es `x⁻ = x` y `P⁻ = P + Q·Δt`. Después corrige en secuencia con cada medición `z` de varianza `R`: `K = P⁻/(P⁻+R)`, `x = x⁻ + K(z − x⁻)`, `P = (1 − K)P⁻`. Las mediciones son:

- la cámara (R = 0,64);
- el Wi-Fi, si llega, a 0,8 personas por dispositivo y con un error relativo del 30 %.

Si la cámara cae, el filtro conserva la última estimación (o usa el Wi-Fi) y la varianza aumenta con el tiempo.

**Puestos.** El algoritmo de ray casting (regla par-impar) asigna cada objeto a su puesto según el centro de su caja. Una persona en el puesto lo deja `OCUPADO_VERIFICADO`. Pertenencias sin persona lo pasan a `ALERTA_ACAPARAMIENTO` y arranca el cronómetro; a los 15 minutos el puesto queda `LIBERADO_POR_ABANDONO`.

Todos los parámetros están en `app.twin.*` y son **ilustrativos**: no son mediciones calibradas.

## Rol por zona y escenarios reconocidos

**Docente adelante, estudiantes al fondo.** La franja frontal del salón (`y < 0,30`, sin pupitres) es la zona del docente. En cada frame, una persona en esa franja cuenta como docente y una dentro de la región de un pupitre como estudiante; las de los pasillos entran en el conteo fusionado pero en ningún rol. Se reconoce por **dónde** está cada persona, nunca por quién es; quién es lo dice el check-in del titular en el kiosco.

**Escenarios.** Cada respuesta del gemelo (REST y STOMP) trae `escenarios`, reconocidos sobre el estado con un detector por escenario:

| Escenario | Se reconoce cuando |
|---|---|
| `CLASE_EN_CURSO` | Hay un docente en su zona y al menos un estudiante en su puesto |
| `FALLO_CAMARA` | La cámara se reportó caída o lleva más de 2 min sin enviar (un salón sin cámara no falla) |
| `SOBRECUPO`, `NO_SHOW`, `SUBUTILIZADO`, `POPUP_PARCIALES` | El estado de reserva y aforo lo dice |
| `MANTENIMIENTO` | El salón está fuera de servicio |
| `ACAPARAMIENTO` | Un puesto tiene pertenencias sin persona |

El backend no simula nada. Cualquier fuente de datos usa las mismas rutas y su propia clave de dispositivo; si sus frames no vienen de una cámara real los marca con `emulated: true`, el gemelo lo guarda en `Sensing.emulated` y el tablero muestra la insignia "Emulado".

## Disponibilidad

`GET /api/v1/disponibilidad?fecha=YYYY-MM-DD` (cualquier rol; sin fecha, hoy en `America/Bogota`) devuelve, para cada espacio de estudiantes, franjas de una hora entre las 7:00 y las 22:00:

| Estado | De dónde sale |
|---|---|
| `CLASE` | El horario académico (`horario/horario-isw-2620.csv`, generado con `tools/horario`) |
| `RESERVADO` | Una reserva del sistema |
| `OCUPADO` | La hora actual, si la cámara ve personas |
| `PROGRAMADO_SIN_USO` | Una clase o reserva en curso hace 15 min (`NO_SHOW_GRACE`) sin nadie: libre en la práctica |
| `LIBRE` | Nada de lo anterior |

Cada espacio trae además `ahora`, `personas`, `conCamara` y `libreHasta` (la hora de la próxima clase o reserva). Cada 30 s, `vigilarHorario` revisa las clases en curso y registra `HORARIO_SIN_USO` («Programado para X y nadie llegó: libre») con el enfriamiento de alertas, para que el administrador pueda ofrecer el espacio. En otra fecha no hay cámara: solo cuentan el horario y las reservas.

## Puestos de trabajo y CPU

Un puesto es la mesa, la silla y el computador de un asiento de la rejilla (Composite `PuestoDeTrabajo`); su código es la región de cámara de ese asiento (`301-A-P07`). El agente de cada PC envía un latido por minuto (`POST /api/v1/workstations/heartbeats`): con el salón vacío y la cámara en línea, un computador que sigue reportando levanta `CPU_ENCENDIDA_SIN_USUARIO`.

**Registro automático:** la cámara propone los puestos con el salón vacío (`/workstations/discovery`), cada PC muestra un patrón en su ranura y la cámara reporta dónde lo vio (`/workstations/pairing`), y el administrador revisa y confirma (`/admin/spaces/{id}/propuesta`). Con el inventario confirmado, `/workstations/audit` compara lo que se ve con lo registrado y levanta `ACTIVO_FALTANTE`.

## Probar con MQTT local

```sh
docker compose up --build -d          # PostgreSQL, MongoDB, Mosquitto y backend
# cámara real (repo de IA): python -m edge.calibrar --space-id 301-A --source 0
#                            python -m edge.yolo_edge_publisher --calibracion calibracion-301A.json --source 0 --mqtt localhost
# frame de cámara sin cámara (3 personas en la sala de estudio privado, aforo 2 → SOBRECUPO):
mosquitto_pub -t ucc/pasto/bloque_a/piso_3/HEMEROTECA-3A-ESTUDIO/camara \
  -m '{"space_id":"HEMEROTECA-3A-ESTUDIO","camera_online":true,"detections":[{"track_id":1,"class":"person","confidence":0.9,"bbox":[0.2,0.3,0.26,0.42]},{"track_id":2,"class":"person","confidence":0.9,"bbox":[0.6,0.3,0.66,0.42]},{"track_id":3,"class":"person","confidence":0.9,"bbox":[0.4,0.7,0.46,0.82]}]}'
```
