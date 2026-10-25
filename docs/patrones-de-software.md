# Patrones de software en CampusNexo Backend

Cada patrón se justifica por un problema concreto del caso, con participantes reales, una colaboración que se ejecuta en producción y una prueba automatizada que la ejercita. La arquitectura hexagonal organiza las dependencias, pero **no se cuenta** como patrón GoF.

## Resumen

| # | Categoría | Patrón | Problema del caso que resuelve | Participantes | Prueba automatizada |
|---|---|---|---|---|---|
| 1 | Creacional | **Abstract Factory** | Cada sede necesita una familia coherente de clima: qué es confort, cómo se protege el compresor y qué algoritmos térmico, de ventilación y de iluminación se usan juntos. | `ClimatePolicyFactory`; `AndeanClimatePolicyFactory`, `TropicalClimatePolicyFactory`, `MildClimatePolicyFactory`; productos `ComfortBand`, `CompressorGuard`, `ThermalPolicy`, `VentilationPolicy`, `LightingPolicy`; registro `ClimateFamilies`; clientes `ClimateRecommendationService` **y** `TwinDecisionEngine` (el gemelo). | `ClimatePolicyFactoryTest`, `ComfortBandTest`, `ClimateRecommendationServiceTest`, `TwinPipelineTest` |
| 2 | Creacional | **Builder** | Dos objetos inmutables con muchas partes: la recomendación auditable (diez partes, cuatro opcionales) y el estado del gemelo (quince partes que cada decisión copia y cambia en parte). | `ClimateRecommendation.Builder`; `SpaceTwinState.Builder` (`toBuilder()`); directores `ClimateRecommendationService` y `TwinDecisionEngine`. | `ClimateRecommendationTest`, `TwinPipelineTest` |
| 3 | Estructural | **Adapter** | El servicio de IA en Python habla HTTP/JSON `snake_case`, con su clave y sus códigos de error; el núcleo solo conoce `OccupancyEstimator`. | Target `OccupancyEstimator`; Adapter `HttpOccupancyEstimatorAdapter`; Adaptee la API FastAPI `/v1/occupancy/estimate`. | `HttpOccupancyEstimatorAdapterTest` (servidor HTTP simulado: éxito, espacio cambiado, 5xx, 429 con `Retry-After`, JSON roto, *timeout*, clave) |
| 4 | Estructural | **Decorator** | Reintentar, cortar el circuito y tener respaldo son responsabilidades que se apilan sobre el adaptador sin modificarlo. Igual con la protección del compresor sobre cualquier algoritmo térmico. | Componente `OccupancyEstimator`; decoradores `RetryingOccupancyEstimator`, `CircuitBreakerOccupancyEstimator`, `FallbackOccupancyEstimator`; `MinimumCycleThermalPolicy` sobre `ThermalPolicy`. | `OccupancyDecoratorsTest`, `ResilienceChainTest`, `MinimumCycleThermalPolicyTest` |
| 5 | Estructural | **Facade** | Evaluar un espacio exige coordinar catálogo, telemetría, IA, clima, reservas y alertas en un orden preciso. | `CampusOperationsFacade` (`SpaceEvaluationUseCase`) sobre los casos de uso de cada subsistema. | `CampusOperationsFacadeTest` |
| 6 | Estructural | **Proxy** | El catálogo y las regiones de pupitre solo cambian con una migración, pero cada frame y cada petición los buscaba en PostgreSQL. | Sujeto `SpaceCatalogRepository` / `SeatRegionRepository`; proxies `CachingSpaceCatalogRepository`, `CachingSeatRegionRepository`; sujetos reales los adaptadores JPA. | `CachingProxiesTest` (100 búsquedas, 1 consulta) |
| 7 | Comportamiento | **Strategy** | Los algoritmos térmicos, de ventilación y de iluminación son intercambiables por familia. | `ThermalPolicy` (3), `VentilationPolicy` (2), `LightingPolicy` (2); contextos `ClimateRecommendationService` y la regla `FamilyStrategyRule` del gemelo. | `ThermalPolicyTest`, `VentilationAndLightingPolicyTest`, `HvacSettingChainTest` |
| 8 | Comportamiento | **Observer** | Una lectura debe disparar varias reacciones independientes (CO₂, sobrecupo, consumo en vacío, reserva sin uso). | Sujeto `TelemetryEventPublisher`; `TelemetryObserver` y sus observadores en `application/alert`; evento `TelemetryRecorded`. | `AlertObserversTest`, `TelemetryEventPublisherTest` |
| 9 | Comportamiento | **Chain of Responsibility** | Quién decide qué hace el equipo de clima de un salón: sin equipo, mantenimiento, control del docente, protección del compresor, familia climática o control predictivo, en ese orden de precedencia. Antes eran ternarios anidados. | Handler `HvacSettingRule`; cadena `HvacSettingChain`; reglas `NoClimateUnitRule`, `MaintenanceRule`, `ManualOverrideRule`, `CompressorProtectionRule`, `FamilyStrategyRule`, `PredictiveControlRule`. | `HvacSettingChainTest` |
| 10 | Comportamiento | **State** | El *circuit breaker* frente a la IA se comporta distinto cerrado, abierto o semiabierto, y cada estado decide su transición. | Contexto `CircuitBreakerOccupancyEstimator`; estados `Closed`, `Open`, `HalfOpen` (interfaz `BreakerState`). | `CircuitBreakerOccupancyEstimatorTest` |
| 11 | Estructural | **Composite** | Un puesto de trabajo es mesa, silla y computador; la administración suma su potencia o pregunta si funciona igual que a un activo suelto. | Componente `ElementoDeInventario`; hojas `Cpu`, `Silla`, `Mesa` (clase abstracta `Activo`); compuesto `PuestoDeTrabajo`. | `PuestoDeTrabajoTest`, `CpuTest` |

Los **cinco patrones vistos en clase** (Abstract Factory, Builder, Adapter, Decorator y Facade) son la selección principal. Proxy, Strategy, Observer, Chain of Responsibility, State y Composite son patrones investigados adicionalmente, cada uno resolviendo un problema medible del caso. Los escenarios operativos reconocidos son otra aplicación de **Strategy**: un `DetectorDeEscenario` por escenario, recorridos por `ReconocedorDeEscenarios` (`DetectoresDeEscenarioTest`).

## Patrones en la comunicación del sistema

Cada flecha entre las cuatro capas pasa por al menos un patrón:

| Comunicación | Patrón | Por qué |
|---|---|---|
| Borde (cámara, Arduino, PCs) → backend | **Adapter** de entrada (`TwinIngestionController`, adaptadores MQTT) | Traducen JSON `snake_case` y tópicos MQTT al dominio, que no conoce ni HTTP ni MQTT. |
| Backend → IA Python | **Adapter** + **Decorator** + **State** | `HttpOccupancyEstimatorAdapter` y `HttpAiAdvisorAdapter` traducen el contrato; reintento, circuito y respaldo envuelven al adaptador sin tocarlo. |
| Backend → PostgreSQL | **Adapter** (JPA) + **Proxy** (caché del catálogo) | El núcleo solo ve puertos; el catálogo, que casi no cambia, no se consulta en cada frame. |
| Gemelo → tablero y MongoDB | **Observer** (`TwinChangeNotifierPort` × N) | El cambio confirmado se entrega a cada seguidor; si MongoDB cae, el tablero igual lo recibe. |
| Lectura → alertas | **Observer** (`TelemetryEventPublisher`) | Cada regla de alerta reacciona sola; agregar una no toca la ingesta. |
| Frontend → caso de evaluación | **Facade** (`CampusOperationsFacade`) | Una petición coordina catálogo, telemetría, IA, clima, reservas y alertas. |

## 1. Abstract Factory: un solo cerebro de clima

La familia define **todo lo que significa clima en esa sede**: la banda de confort, la protección del compresor y los tres algoritmos. Y la leen los dos decisores del campus:

```text
Familia (ClimatePolicyFactory) de la sede
 ├─ comfortBand() ───────────► ThermalInertiaMpcDomainService   (gemelo: cuándo encender, pre-calentar, cortar)
 ├─ createThermalStrategy() ─► FamilyStrategyRule               (gemelo: qué modo — calentar, enfriar, deshumidificar)
 ├─ compressorGuard() ───────► CompressorProtectionRule         (gemelo) y MinimumCycleThermalPolicy (recomendación)
 └─ createThermalPolicy(), createVentilationPolicy(), createLightingPolicy()
                              ► ClimateRecommendationService    (POST /climate-recommendations y /evaluations)
```

| Familia | Banda | Térmica | Ventilación | Iluminación |
|---|---|---|---|---|
| Andina (Pasto) | 19–24 °C | `HeatingFirstThermalPolicy`: calefacción bajo 19 °C | `Co2ThresholdVentilationPolicy`, ventanas cerradas | `DaylightHarvestingPolicy` |
| Tropical (Barranquilla) | 23–26 °C | `CoolingWithDehumidificationPolicy`: enfría y deshumidifica | `Co2ThresholdVentilationPolicy` (900 ppm) | `GlareControlLightingPolicy` |
| Templada (Medellín) | 20–25 °C | `ComfortBandThermalPolicy`: prefiere el enfriamiento libre | `NaturalVentilationPolicy` | `DaylightHarvestingPolicy` |

Mezclar familias sería incoherente, y ahora también es imposible entre el gemelo y la recomendación: no hay una segunda tabla de bandas. `ClimateFamilies` verifica al arrancar que exista exactamente una fábrica por perfil.

## 2. Builder

`ClimateRecommendation.Builder` arma la recomendación a medida que salen las tres decisiones y aplica los valores por defecto de las partes opcionales. `SpaceTwinState.Builder` resuelve el otro extremo: un estado de quince partes (cada una un *value object*: `HvacSetting`, `Environment`, `Sensing`, `ManualControls`, `RoomTimers`…) que cada decisión copia y cambia en parte. Sin él, cada cambio era un constructor de 22 argumentos en orden fijo, donde intercambiar dos `double` compilaba sin aviso. Es la variante fluida para objetos inmutables, no el Builder GoF con varias representaciones.

## 3. Adapter

`HttpOccupancyEstimatorAdapter` convierte `OccupancyQuery` en el JSON que espera FastAPI (con el conteo **crudo** de la cámara, el área y la capacidad del salón) y envía la clave `X-API-Key`. Traduce los errores: transporte, 5xx y 429 → `OccupancyEstimatorUnavailableException` (con el `Retry-After` del servicio, si lo hay); respuesta incompleta, fuera de rango o de otro espacio → `OccupancyEstimatorInvalidResponseException` (502, sin reenviar al cliente el texto del servicio). El dominio no importa clases HTTP.

## 4. Decorator

```text
FallbackOccupancyEstimator               ← si todo falla: la misma fusión, calculada en Java (RESPALDO_LOCAL)
  └─ CircuitBreakerOccupancyEstimator    ← tras 3 fallas deja de llamar 30 s (o lo que pida Retry-After)
       └─ RetryingOccupancyEstimator     ← 2 intentos, espera 100 ms × 2^n con jitter
            └─ HttpOccupancyEstimatorAdapter (Adapter)
```

La composición se arma en `ApplicationConfiguration` y se ajusta por configuración (`AI_MAX_ATTEMPTS`, `AI_BACKOFF`, `AI_CIRCUIT_*`, `AI_FALLBACK_ENABLED`). Con la IA caída, la primera evaluación tarda como máximo 2 × (1 s + 2 s) + 0,15 s; con el circuito abierto, el respaldo responde en microsegundos. Cada respaldo se cuenta en `campusnexo_ai_fallbacks_total`.

## 5. Facade

`POST /api/v1/spaces/{id}/evaluations` toma la última lectura, estima la ocupación con el **conteo propio de la cámara** (nunca con la estimación ya fusionada del gemelo, que contaría dos veces el CO₂ y el Wi-Fi), genera y guarda la recomendación con el estado HVAC **real** que el gemelo comandó y devuelve la reserva activa y las alertas recientes.

## 6. Proxy

`CachingSpaceCatalogRepository` y `CachingSeatRegionRepository` tienen la interfaz del repositorio y responden desde memoria; cargan todo en una consulta por tiempo de vida (`CATALOG_CACHE_TTL`, 5 min). Junto con las consultas en lote de `TwinQueryService`, la vista del campus pasó de unas 216 consultas por petición (dos por salón) a cuatro.

## 7. Strategy

Los clientes solo conocen las interfaces. Un mismo ambiente (17 °C, 60 % HR) produce calefacción en la familia andina y climatización apagada en la tropical; con 82 % de humedad y la temperatura en banda, la estrategia tropical deshumidifica, y el gemelo lo aplica a través de `FamilyStrategyRule`.

## 8. Observer

Después de guardar una lectura, y **solo cuando la transacción confirma** (`UnitOfWork.afterCommit`), `TelemetryService` publica `TelemetryRecorded`. Cada observador evalúa una regla y guarda la alerta con un periodo de enfriamiento. El repositorio hace la comprobación y la inserción en un solo paso atómico (bloqueo *advisory* de PostgreSQL por salón y tipo), así que dos hilos o dos instancias no duplican una alerta. La entrega es asíncrona con hasta 3 intentos; si el grupo de hilos está saturado, la entrega se reporta en lugar de ejecutarse en el hilo de la cámara: la alerta se deriva de nuevo con la siguiente lectura, mientras que un *pipeline* bloqueado perdería frames.

## 9. Chain of Responsibility

```text
NoClimateUnitRule → MaintenanceRule → ManualOverrideRule → CompressorProtectionRule → FamilyStrategyRule → PredictiveControlRule
   sin equipo         apaga ya            docente: centro       retiene el cambio si       la familia elige      propone la
   → SIN_CLIMATIZ.                         de la banda           acortaría el ciclo          el modo               estrategia (MPC)
```

Cada regla decide o pasa la petición con `next.proceed(request)`; `CompressorProtectionRule` y `FamilyStrategyRule` además ajustan lo que propuso el resto de la cadena. El orden **es** la precedencia y se prueba (`rulesAreOrderedByPrecedence`). Agregar una regla (por ejemplo, horario de mantenimiento preventivo) es una clase nueva y una línea en `HvacSettingChain.standard()`.

## 10. State

`CircuitBreakerOccupancyEstimator` delega cada llamada en su estado actual. `Closed` cuenta fallas consecutivas y pasa a `Open`; `Open` falla de inmediato hasta su instante de reapertura y entonces pasa a `HalfOpen`; `HalfOpen` permite una sola llamada de prueba (las concurrentes fallan rápido) y vuelve a `Closed` o a `Open`. Un 429 con `Retry-After` abre el circuito de inmediato por el tiempo pedido. Las transiciones se publican en `campusnexo_ai_circuit_transitions_total`.

## Guion de demostración (aprox. 10 minutos)

1. `GET /api/v1/spaces`: el campus UCC Pasto, 107 espacios del plano más los pilotos 301-A y 303-A.
2. Con los dispositivos de 301-A y 303-A enviando: 301-A registra sus 30 puestos por la cámara (**Composite**) y luego reconoce `CLASE_EN_CURSO` con el docente adelante (**Strategy** de escenarios); la traza muestra la cadena HVAC y la familia andina.
3. `CO2_ALTO` en 301-A y `FALLO_CAMARA` en 303-A: ventilación con prioridad; sin cámara, el Kalman sigue con CO₂ y Wi-Fi.
4. `POST /api/v1/spaces/421-A/evaluations` con la IA apagada: `RESPALDO_LOCAL` (**Decorator**, **Adapter**), recomendación `CALEFACCION` (**Facade**, **Abstract Factory**, **Strategy**, **Builder**). Repetir tres veces: el circuito se abre (**State**) y `/actuator/prometheus` lo muestra.
5. Con la IA encendida: `SERVICIO_IA`, con el peso de cada fuente.
6. Pruebas: `HvacSettingChainTest` (precalentamiento en Pasto, deshumidificación tropical, protección del compresor) y `FusionModelTest` (los mismos casos que el servicio Python).
7. Fin de la clase en 301-A: los PC que quedan encendidos generan `CPU_ENCENDIDA_SIN_USUARIO` y la orden de apagado.

## Lo que **no** se cuenta como patrón

- **`HvacCommand` y `EquipmentCommand` no son Command.** Son mensajes de datos para el ESP32 y el Arduino; no hay invocador, ejecución diferida ni deshacer.
- **`HvacControlState` no es el patrón State.** Es un enum con datos (potencia, compresor encendido); quien decide las transiciones es la cadena de reglas. El State contabilizado es el del *circuit breaker*.
- **Los beans de Spring no son Singleton GoF**: es el ciclo de vida del contenedor.
- **La arquitectura hexagonal, REST, JPA, Docker y los repositorios separados** son arquitectura y tecnología.
- **`TwinWriter`** es control de concurrencia optimista; **`UnitOfWork`** y la *outbox* son patrones de arquitectura empresarial (Fowler), no GoF.
- **`MqttInbox`** es un productor-consumidor acotado con confirmación posterior al procesamiento: concurrencia, no GoF.
- **La fusión del servicio Python** es una función; no se presenta como Strategy.

## Preguntas difíciles y respuestas con evidencia

| Pregunta | Respuesta |
|---|---|
| ¿Por qué hay un MPC y una Abstract Factory? ¿No son dos cerebros? | Es uno. La familia define el confort, el modo y la protección del compresor; el MPC solo decide *cuándo*, simulando la inercia. Pasto usa la familia andina, por eso el MPC precalienta antes de la clase de las 7 (`preHeatsAColdRoomBeforeTheClass`). La recomendación lee el estado que el gemelo comandó (`HvacStateResolver`). |
| ¿La IA recibe el conteo de la cámara o su propio Kalman? | El conteo crudo (`telemetry_stream.camera_people`), o `null` si la cámara está caída. Mandarle la estimación fusionada contaría dos veces el CO₂ y el Wi-Fi. |
| ¿Cuántos modelos de ocupación hay? | Uno: el gemelo (con memoria), el respaldo local y el servicio Python (sin memoria) usan la misma fusión y los mismos parámetros, y los dos repositorios prueban el mismo `fusion_golden_cases.csv`. |
| Con MQTT QoS 1, ¿qué pasa si un frame llega dos veces? | Se ignora: cada salón guarda la hora de captura de su último frame y de su última lectura del Arduino, y `UNIQUE (space_id, recorded_at)` lo garantiza también en la base. El mensaje se confirma al broker después de procesarlo, con sesión persistente: una caída no pierde frames. Evidencia: `redeliveredFrameIsIgnored`, `redeliveredEdgeFrameIsIgnoredAndCounted`, `MqttInboxTest`. |
| ¿Y si el frame trae una fecha imposible? | Se rechaza antes de tocar nada (`ReadingAdmission`): ni el gemelo ni el historial cambian (`invalidTimestampChangesNothing`). |
| ¿Qué pasa si la IA se cae en plena sustentación? | La primera evaluación responde con el respaldo en ≈ 6 s como máximo; desde la tercera falla el circuito se abre y el respaldo responde al instante. Evidencia: `ResilienceChainTest`, `CircuitBreakerOccupancyEstimatorTest`. |
| ¿Hay simulación en el sistema? | No. El backend no tiene código de simulación: los dispositivos envían por la API con su propia clave, y lo que no viene de una cámara real llega marcado `emulated: true` y el tablero lo muestra. Cambiar de fuente es cambiar quién envía, no el sistema. |
| ¿Cualquier operador puede cancelar cualquier reserva? | No: solo su autor o un administrador (`reservation_not_owned`, 403). |
| ¿De dónde sale el ahorro en pesos? | De un contrafactual declarado: el equipo encendido todo el horario, y solo en espacios con equipo (`hvac_installed`). Es una estimación de modelo, no una medición del contador. |
| ¿Qué significa `confidence_pct = 90`? | `1 − σ/capacidad`: el margen de error es el 10 % del salón. No es probabilidad de acierto; `eval/evaluate.py` mide MAE, RMSE, sesgo y calibración con conteos reales. |

## Límites que se deben decir en la defensa

- Umbrales, setpoints y parámetros de la fusión son valores de referencia, no calibrados con instrumentos del campus.
- Qué salones tienen equipo de clima es un supuesto del prototipo que debe confirmar la planta física; las medidas de 301-A y 303-A son ilustrativas hasta medirlos.
- No hay mediciones de error de la ocupación ni de ahorro todavía; la herramienta para medirlos sí existe.
- El gemelo publica órdenes al ESP32 y al Arduino por MQTT, pero no se ha probado con equipos reales.
- El backend recibe conteos agregados; no recibe ni guarda imágenes.
