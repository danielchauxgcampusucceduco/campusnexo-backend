# Patrones de software en CampusNexo Backend

Cada patrón se justifica por un problema concreto del caso, con participantes reales, una colaboración que se ejecuta en producción y una prueba automatizada que la ejercita. La arquitectura hexagonal organiza las dependencias, pero **no se cuenta** como patrón GoF.

El sistema funciona solo con la **cámara de visión PoE** de cada espacio. La cámara va cableada al portátil de borde, que está en la red del campus por Ethernet. No hay sensores de ambiente ni actuadores, así que el sistema no controla clima, luces ni consumo. Su foco es saber **qué espacios para estudiantes están libres, en qué horario**, e informar al administrador.

## Resumen

| # | Categoría | Patrón | Problema del caso que resuelve | Participantes | Prueba automatizada |
|---|---|---|---|---|---|
| 1 | Creacional | **Builder** | El estado del gemelo tiene diez partes, y cada decisión lo copia y cambia en parte. | `SpaceTwinState.Builder` (`toBuilder()`); director `TwinDecisionEngine`. | `TwinPipelineTest` |
| 2 | Estructural | **Adapter** | El servicio de IA en Python habla HTTP/JSON `snake_case`, con su clave y sus códigos de error; el núcleo solo conoce `OccupancyEstimator`. | Target `OccupancyEstimator`; Adapter `HttpOccupancyEstimatorAdapter`; Adaptee la API FastAPI `/v1/occupancy/estimate`. | `HttpOccupancyEstimatorAdapterTest` (servidor HTTP simulado: éxito, espacio cambiado, 5xx, 429 con `Retry-After`, JSON roto, *timeout*, clave) |
| 3 | Estructural | **Decorator** | Reintentar, cortar el circuito y tener respaldo son responsabilidades que se apilan sobre el adaptador sin modificarlo. | Componente `OccupancyEstimator`; decoradores `RetryingOccupancyEstimator`, `CircuitBreakerOccupancyEstimator`, `FallbackOccupancyEstimator`. | `OccupancyDecoratorsTest`, `ResilienceChainTest` |
| 4 | Estructural | **Facade** | Evaluar un espacio exige coordinar catálogo, telemetría, IA de ocupación, reservas y alertas en un orden preciso. | `CampusOperationsFacade` (`SpaceEvaluationUseCase`) sobre los casos de uso de cada subsistema. | `CampusOperationsFacadeTest` |
| 5 | Estructural | **Proxy** | El catálogo y las regiones de pupitre solo cambian con una migración, pero cada frame y cada petición los buscaba en PostgreSQL. | Sujeto `SpaceCatalogRepository` / `SeatRegionRepository`; proxies `CachingSpaceCatalogRepository`, `CachingSeatRegionRepository`; sujetos reales los adaptadores JPA. | `CachingProxiesTest` (100 búsquedas, 1 consulta) |
| 6 | Estructural | **Composite** | Un puesto de trabajo es mesa, silla y computador; la administración pregunta si funciona igual que a un activo suelto. | Componente `ElementoDeInventario`; hojas `Cpu`, `Silla`, `Mesa` (clase abstracta `Activo`); compuesto `PuestoDeTrabajo`. | `PuestoDeTrabajoTest`, `CpuTest` |
| 7 | Comportamiento | **Observer** | Una lectura debe disparar reacciones independientes: sobrecupo, reserva sin uso y registro. | Sujeto `TelemetryEventPublisher`; `TelemetryObserver` y sus observadores (`CapacityAlertObserver`, `ReservationNoShowObserver`, `LoggingTelemetryObserver`); evento `TelemetryRecorded`. El cambio del gemelo también tiene seguidores: `TwinChangeNotifierPort` × N (tablero por STOMP e historial en MongoDB). | `AlertObserversTest`, `TelemetryEventPublisherTest` |
| 8 | Comportamiento | **State** | El *circuit breaker* frente a la IA se comporta distinto cerrado, abierto o semiabierto, y cada estado decide su transición. | Contexto `CircuitBreakerOccupancyEstimator`; estados `Closed`, `Open`, `HalfOpen` (interfaz `BreakerState`). | `CircuitBreakerOccupancyEstimatorTest` |
| 9 | Comportamiento | **Strategy** | Cada escenario operativo (clase en curso, sobrecupo, no-show, subutilizado, popup, mantenimiento, acaparamiento, fallo de cámara) se reconoce con un algoritmo propio e intercambiable. | Estrategia `DetectorDeEscenario` (8 implementaciones); contexto `ReconocedorDeEscenarios`. | `DetectoresDeEscenarioTest` |

De los patrones vistos en clase quedan **Builder, Adapter, Decorator y Facade**. Proxy, Composite, Observer, State y Strategy son patrones investigados aparte, y cada uno resuelve un problema medible del caso. En total son nueve, más que los cinco que exige el curso.

Abstract Factory y Chain of Responsibility salieron con el control de clima, porque solo existían para él: la familia climática por sede y la cadena de reglas del equipo HVAC.

## Patrones en la comunicación del sistema

Cada flecha entre las capas pasa por al menos un patrón:

| Comunicación | Patrón | Por qué |
|---|---|---|
| Borde (cámara PoE y PCs) → backend | **Adapter** de entrada (`TwinIngestionController`, `YoloMqttTelemetryInboundAdapter`) | Traducen JSON `snake_case` y tópicos MQTT al dominio, que no conoce ni HTTP ni MQTT. |
| Backend → IA Python | **Adapter** + **Decorator** + **State** | `HttpOccupancyEstimatorAdapter` y `HttpAiAdvisorAdapter` traducen el contrato; reintento, circuito y respaldo envuelven al adaptador sin tocarlo. |
| Backend → PostgreSQL | **Adapter** (JPA) + **Proxy** (caché del catálogo) | El núcleo solo ve puertos; el catálogo, que casi no cambia, no se consulta en cada frame. |
| Gemelo → tablero y MongoDB | **Observer** (`TwinChangeNotifierPort` × N) | El cambio confirmado se entrega a cada seguidor; si MongoDB cae, el tablero igual lo recibe. |
| Lectura → alertas | **Observer** (`TelemetryEventPublisher`) | Cada regla de alerta reacciona sola; agregar una no toca la ingesta. |
| Frontend → caso de evaluación | **Facade** (`CampusOperationsFacade`) | Una petición coordina catálogo, telemetría, IA, reservas y alertas. |

## 1. Builder

`SpaceTwinState.Builder` arma un estado de diez partes: estado del salón, ocupación, último paso del Kalman, `Sensing`, puestos, presencia, `ManualControls`, `RoomTimers` y la hora. Cada decisión lo copia y cambia en parte. Sin él, cada cambio era un constructor largo en orden fijo, donde intercambiar dos valores del mismo tipo compilaba sin aviso. Es la variante fluida para objetos inmutables, no el Builder GoF con varias representaciones.

## 2. Adapter

`HttpOccupancyEstimatorAdapter` convierte `OccupancyQuery` en el JSON que espera FastAPI y envía la clave `X-API-Key`. Ese JSON lleva el conteo **crudo** de la cámara, el Wi-Fi si llega, y el área y la capacidad del salón.

También traduce los errores:
- transporte, 5xx y 429 → `OccupancyEstimatorUnavailableException`, con el `Retry-After` del servicio si lo hay;
- respuesta incompleta, fuera de rango o de otro espacio → `OccupancyEstimatorInvalidResponseException` (502, sin reenviar al cliente el texto del servicio).

El dominio no importa clases HTTP.

## 3. Decorator

```text
FallbackOccupancyEstimator               ← si todo falla: la misma fusión, calculada en Java (RESPALDO_LOCAL)
  └─ CircuitBreakerOccupancyEstimator    ← tras 3 fallas deja de llamar 30 s (o lo que pida Retry-After)
       └─ RetryingOccupancyEstimator     ← 2 intentos, espera 100 ms × 2^n con jitter
            └─ HttpOccupancyEstimatorAdapter (Adapter)
```

La composición se arma en `ApplicationConfiguration` y se ajusta por configuración (`AI_MAX_ATTEMPTS`, `AI_BACKOFF`, `AI_CIRCUIT_*`, `AI_FALLBACK_ENABLED`). Cada respaldo se cuenta en `campusnexo_ai_fallbacks_total`.

## 4. Facade

`POST /api/v1/spaces/{id}/evaluations` toma la última lectura y estima la ocupación con el **conteo propio de la cámara**, nunca con la estimación ya fusionada del gemelo. Devuelve también la reserva activa y las alertas recientes.

## 5. Proxy

`CachingSpaceCatalogRepository` y `CachingSeatRegionRepository` tienen la interfaz del repositorio y responden desde memoria. Cargan todo en una consulta por tiempo de vida (`CATALOG_CACHE_TTL`, 5 min). Junto con las consultas en lote de `TwinQueryService`, la vista del campus pasó de unas 216 consultas por petición a cuatro.

## 6. Composite

`PuestoDeTrabajo` agrupa mesa, silla y CPU, y responde a `ElementoDeInventario` igual que un activo suelto. El agente de latido de cada PC alimenta la `Cpu`, y la alerta `CPU_ENCENDIDA_SIN_USUARIO` usa esa información cuando la cámara no ve a nadie en el puesto.

## 7. Observer

`TelemetryService` publica `TelemetryRecorded` después de guardar una lectura, y **solo cuando la transacción confirma** (`UnitOfWork.afterCommit`). Cada observador evalúa una regla y guarda la alerta con un periodo de enfriamiento.

El repositorio hace la comprobación y la inserción en un solo paso atómico, con un bloqueo *advisory* de PostgreSQL por salón y tipo. Así, dos hilos o dos instancias no duplican una alerta. La entrega es asíncrona, con hasta 3 intentos.

## 8. State

`CircuitBreakerOccupancyEstimator` delega cada llamada en su estado actual:
- `Closed` cuenta las fallas consecutivas y pasa a `Open`;
- `Open` falla de inmediato hasta su instante de reapertura y entonces pasa a `HalfOpen`;
- `HalfOpen` permite una sola llamada de prueba y vuelve a `Closed` o a `Open`.

Un 429 con `Retry-After` abre el circuito de inmediato por el tiempo pedido. Las transiciones se publican en `campusnexo_ai_circuit_transitions_total`.

## 9. Strategy

`ReconocedorDeEscenarios` recorre la lista de `DetectorDeEscenario`, y cada detector solo lee el gemelo. ArchUnit lo verifica. Agregar un escenario es agregar una clase y registrarla en la lista; el reconocedor no cambia.

## Disponibilidad: dónde se usa lo anterior

`DisponibilidadService` (`GET /api/v1/disponibilidad`) no introduce un patrón nuevo, sino que reutiliza los existentes:
- lee el horario académico por un puerto (`HorarioAcademicoRepository`, con `CsvHorarioAcademicoAdapter` como **Adapter**);
- lee el catálogo por el **Proxy**;
- lee el gemelo que arma el **Builder**.

Para cada espacio de estudiantes construye franjas de una hora, de 7:00 a 22:00, con estos estados:

| Estado | De dónde sale |
|---|---|
| CLASE | El horario académico |
| RESERVADO | Las reservas |
| OCUPADO | Lo que ve la cámara en la hora actual |
| LIBRE | Ninguna de las anteriores |
| PROGRAMADO_SIN_USO | Una clase sin nadie después de 15 minutos |

En el último caso, el chequeo programado (`vigilarHorario`) registra la alerta `HORARIO_SIN_USO` para el administrador (`DisponibilidadServiceTest`).

## Guion de demostración (aprox. 10 minutos)

1. `GET /api/v1/spaces`: el campus UCC Pasto, con los espacios del plano, los pilotos 301-A y 303-A y las dos salas de la hemeroteca.
2. Un frame de cámara en `HEMEROTECA-3A-ESTUDIO` con 3 personas, que tiene aforo 2: aparece `SOBRECUPO`, entregado a los observadores (**Observer**) y al tablero.
3. Vista **Disponibilidad**: las clases del horario, los libres ahora con «libre hasta las HH:MM» y las clases sin asistentes.
4. `POST /api/v1/spaces/421-A/evaluations` con la IA apagada: responde con `RESPALDO_LOCAL` (**Decorator**, **Adapter**, **Facade**). Al repetirlo tres veces, el circuito se abre (**State**) y `/actuator/prometheus` lo muestra.
5. `FALLO_CAMARA` en 303-A: el gemelo conserva la última estimación y su incertidumbre crece.
6. Fin de la clase en 301-A: los PC que quedan encendidos sin nadie generan `CPU_ENCENDIDA_SIN_USUARIO` (**Composite**).

## Lo que **no** se cuenta como patrón

- **Los beans de Spring no son Singleton GoF**: es el ciclo de vida del contenedor.
- **La arquitectura hexagonal, REST, JPA, Docker y los repositorios separados** son arquitectura y tecnología.
- **`TwinWriter`** es control de concurrencia optimista. **`UnitOfWork`** y la *outbox* son patrones de arquitectura empresarial (Fowler), no GoF.
- **`MqttInbox`** es un productor-consumidor acotado con confirmación posterior al procesamiento: concurrencia, no GoF.
- **La fusión del servicio Python** es una función; no se presenta como Strategy.

## Preguntas difíciles y respuestas con evidencia

| Pregunta | Respuesta |
|---|---|
| ¿Por qué ya no hay control de clima ni de luces? | No hay presupuesto para el módulo de sensores y relés. Sin él no se puede medir ni apagar nada, así que el sistema se centra en lo que la cámara sí sabe: cuántas personas hay y si el espacio está libre. |
| ¿La IA recibe el conteo de la cámara o su propio Kalman? | Recibe el conteo crudo (`telemetry_stream.camera_people`), o `null` si la cámara está caída. Mandarle la estimación fusionada contaría dos veces el Wi-Fi. |
| ¿Cuántos modelos de ocupación hay? | Uno solo. El gemelo (con memoria), el respaldo local y el servicio Python (sin memoria) usan la misma fusión, cámara más Wi-Fi opcional, y los dos repositorios prueban el mismo `fusion_golden_cases.csv`. |
| Con MQTT QoS 1, ¿qué pasa si un frame llega dos veces? | Se ignora. Cada salón guarda la hora de captura de su último frame, y `UNIQUE (space_id, recorded_at)` lo garantiza también en la base. Evidencia: `redeliveredEdgeFrameIsIgnoredAndCounted`, `MqttInboxTest`. |
| ¿Y si el frame trae una fecha imposible? | Se rechaza antes de tocar nada (`ReadingAdmission`). |
| ¿Qué pasa si la IA se cae en plena sustentación? | La primera evaluación responde con el respaldo; desde la tercera falla el circuito se abre y el respaldo responde al instante. Evidencia: `ResilienceChainTest`, `CircuitBreakerOccupancyEstimatorTest`. |
| ¿Cómo sabe el sistema que un salón programado está libre? | Cruza el horario con la cámara. Si a los 15 minutos de empezar la clase no hay nadie, la franja pasa a `PROGRAMADO_SIN_USO` y el administrador recibe `HORARIO_SIN_USO` (`DisponibilidadServiceTest`). |
| ¿Hay simulación en el sistema? | Solo la demo, y se declara. Con `DEMO_SIMULATION=true` y solo después de iniciarla a mano en la vista Simulación, recorre la semana del horario 2620 de Ingeniería de Software y entra al gemelo por el mismo pipeline que una cámara, con cada frame marcado `emulated: true`. «Salir de la simulación» la detiene y borra todo lo emulado (`EmulatedDataService`). |

## Límites que se deben decir en la defensa

- Los umbrales y los parámetros de la fusión son valores de referencia, no calibrados con conteos reales del campus.
- Las medidas de 301-A, 303-A y las salas de la hemeroteca son ilustrativas hasta medirlas.
- Los espacios sin cámara instalada aparecen como «sin cámara»: su disponibilidad sale solo del horario y las reservas.
- El backend recibe conteos agregados; no recibe ni guarda imágenes.
