# Patrones de software en CampusNexo Backend

Cada patrón se justifica por un problema concreto del caso, con participantes reales y una colaboración que se puede ejecutar y probar. La arquitectura hexagonal organiza las dependencias, pero **no se cuenta** como patrón GoF.

## Resumen

| # | Categoría | Patrón | Problema del caso que resuelve | Participantes | Prueba automatizada |
|---|---|---|---|---|---|
| 1 | Creacional | **Abstract Factory** | Cada sede de la universidad necesita una familia coherente de algoritmos de clima: en Pasto se calienta, en Barranquilla se enfría, se deshumidifica y se controla la radiación solar, en Medellín se prefiere la ventilación natural. | `ClimatePolicyFactory` (fábrica abstracta); `AndeanClimatePolicyFactory`, `TropicalClimatePolicyFactory`, `MildClimatePolicyFactory` (fábricas concretas); `ThermalPolicy`, `VentilationPolicy`, `LightingPolicy` (productos abstractos); cliente `ClimateRecommendationService`. | `ClimatePolicyFactoryTest`, `ClimateRecommendationServiceTest` |
| 2 | Creacional | **Builder** | Una recomendación auditable se arma por partes (tres decisiones que salen de estrategias distintas y datos de procedencia opcionales: fuente de la ocupación, instante de la telemetría, entradas usadas). | `ClimateRecommendation.Builder`, producto inmutable `ClimateRecommendation`, director `ClimateRecommendationService`. | `ClimateRecommendationTest` |
| 3 | Estructural | **Adapter** | El servicio de IA en Python habla HTTP/JSON `snake_case` y tiene sus propios errores; el núcleo Java solo conoce `OccupancyEstimator`. | Target `OccupancyEstimator`; Adapter `HttpOccupancyEstimatorAdapter`; Adaptee: API FastAPI `/v1/occupancy/estimate` (a través de `RestClient`). | `CampusNexoApiIntegrationTest` (IA caída) |
| 4 | Estructural | **Decorator** | Si la IA falla, el campus debe seguir operando; reintentar y tener un respaldo son responsabilidades que se agregan sin modificar el adaptador. | Componente `OccupancyEstimator`; decoradores `RetryingOccupancyEstimator`, `FallbackOccupancyEstimator`; componente concreto de respaldo `LocalHeuristicOccupancyEstimator`. Segundo uso: `MinimumCycleThermalPolicy` agrega la protección del compresor a cualquier `ThermalPolicy`. | `OccupancyDecoratorsTest`, `MinimumCycleThermalPolicyTest` |
| 5 | Estructural | **Facade** | Evaluar un espacio exige coordinar catálogo, telemetría, IA, clima, reservas y alertas en un orden preciso. | Fachada `CampusOperationsFacade` (`SpaceEvaluationUseCase`); subsistemas: los casos de uso de catálogo, telemetría, ocupación, clima, reservas y alertas. | `CampusOperationsFacadeTest` |
| 6 | Comportamiento | **Strategy** | Los algoritmos de decisión térmica, de ventilación y de iluminación son intercambiables. | Estrategias `ThermalPolicy` (3 implementaciones), `VentilationPolicy` (2), `LightingPolicy` (2); contexto `ClimateRecommendationService`. | `ThermalPolicyTest`, `VentilationAndLightingPolicyTest` |
| 7 | Comportamiento | **Observer** | Una lectura de sensor debe disparar varias reacciones independientes (CO₂, sobrecupo, consumo en vacío, reserva sin uso). | Sujeto `TelemetryEventPublisher`; observador `TelemetryObserver`; observadores concretos en `application/alert` y `LoggingTelemetryObserver`; evento `TelemetryRecorded`. | `AlertObserversTest`, `TelemetryEventPublisherTest` |

Los **cinco patrones vistos en clase** (Abstract Factory, Builder, Adapter, Decorator y Facade) son la selección principal. Strategy y Observer son patrones investigados adicionalmente, como pide el requisito "investigar más patrones".

## 1. Abstract Factory

`ClimateRecommendationService` obtiene el perfil climático de la sede y pide a su fábrica las tres estrategias. Las familias **no se diferencian solo por números**: cada una combina algoritmos distintos que tienen sentido juntos.

| Familia | Térmica | Ventilación | Iluminación |
|---|---|---|---|
| Andina (Pasto, 14,5 °C promedio) | `HeatingFirstThermalPolicy`: calefacción bajo 19 °C | `Co2ThresholdVentilationPolicy`: mecánica con ventanas cerradas | `DaylightHarvestingPolicy` |
| Tropical (Barranquilla, 31,8 °C) | `CoolingWithDehumidificationPolicy`: enfría y deshumidifica | `Co2ThresholdVentilationPolicy` (900 ppm, edificio cerrado por aire acondicionado) | `GlareControlLightingPolicy`: cierra persianas para reducir la carga térmica |
| Templada (Medellín, 22,8 °C) | `ComfortBandThermalPolicy`: banda amplia, prefiere el enfriamiento libre | `NaturalVentilationPolicy`: abre ventanas si el exterior es apto | `DaylightHarvestingPolicy` |

Mezclar familias sería incoherente (por ejemplo, abrir ventanas en Barranquilla mientras opera el aire acondicionado). Al iniciar, el servicio verifica que exista una fábrica por cada perfil.

```text
POST /api/v1/spaces/{id}/climate-recommendations
  → ClimateRecommendationService
  → ClimatePolicyFactory del perfil de la sede        (Abstract Factory)
  → ThermalPolicy / VentilationPolicy / LightingPolicy (Strategy)
       ThermalPolicy envuelta en MinimumCycleThermalPolicy (Decorator)
  → ClimateRecommendation.Builder                     (Builder)
  → ClimateRecommendationRepository
```

## 2. Builder

El constructor canónico del `record` es el único lugar donde se valida. El `Builder` permite que el servicio arme la recomendación a medida que obtiene cada parte, genera el identificador y aplica los valores por defecto a las partes opcionales (`occupancySource = MANUAL`, sin snapshot de entradas). No se usa Builder "por usarlo": la recomendación tiene diez componentes, cuatro de ellos opcionales.

Diferencia con el Builder GoF clásico: no hay un Builder abstracto con varias representaciones; es la variante con interfaz fluida para objetos inmutables. El papel de director lo cumple `ClimateRecommendationService`. Conviene explicarlo así en la sustentación.

## 3. Adapter

`HttpOccupancyEstimatorAdapter` convierte `OccupancyQuery` en el JSON `snake_case` que espera FastAPI y la respuesta en `OccupancyEstimate`. También traduce los errores: un fallo de transporte o un 5xx produce `OccupancyEstimatorUnavailableException` (HTTP 503); una respuesta incompleta, con datos fuera de rango o de otro espacio produce `OccupancyEstimatorInvalidResponseException` (HTTP 502). El dominio no importa clases HTTP.

## 4. Decorator

```text
FallbackOccupancyEstimator            ← si todo falla, responde la heurística local
  └─ RetryingOccupancyEstimator       ← reintenta si la IA no está disponible
       └─ HttpOccupancyEstimatorAdapter (Adapter)
```

La composición se arma en `ApplicationConfiguration` y se activa o desactiva por configuración (`AI_MAX_ATTEMPTS`, `AI_FALLBACK_ENABLED`). Cada resultado indica su fuente real (`SERVICIO_IA` o `RESPALDO_LOCAL`), que también queda en la recomendación. La heurística local es ilustrativa y reporta una confianza baja a propósito.

## 5. Facade

`POST /api/v1/spaces/{id}/evaluations` ejecuta el flujo completo del caso: toma la última lectura del sensor, estima la ocupación con IA (con respaldo), deriva el estado actual del HVAC desde el historial, genera y guarda la recomendación y devuelve también la reserva activa y las alertas recientes. Antes, el cliente tenía que copiar valores de una respuesta a la siguiente petición.

## 6. Strategy

El cliente (`ClimateRecommendationService`) solo conoce las interfaces. Hay varias implementaciones por interfaz, que se intercambian según la familia. Las pruebas muestran que un mismo ambiente (17 °C, 60 % HR) produce calefacción en la sede andina y climatización apagada en la tropical.

## 7. Observer

Después de guardar una lectura, `TelemetryService` publica `TelemetryRecorded`. Cada observador evalúa una regla y **persiste** la alerta (`GET /api/v1/alerts`), con un periodo de enfriamiento para no repetir la misma alerta en cada lectura. Agregar un observador no exige modificar el publicador: `LoggingTelemetryObserver` se registra solo, como componente de Spring.

La entrega es **asíncrona** (un grupo pequeño de hilos, fuera de la petición) y cada entrega fallida se **reintenta** hasta 3 veces con espera creciente; si sigue fallando se reporta, sin afectar a los demás observadores ni a la lectura guardada. Si la cola se llena, la ingesta hace la entrega ella misma (se frena en vez de perder reacciones). Lo que no garantiza: si el proceso se reinicia con entregas pendientes, se pierden (están en memoria). Para alertas que se recalculan con la siguiente lectura es aceptable; si algún observador tuviera que ejecutarse exactamente una vez, el paso siguiente sería una tabla *outbox* en la base de datos.

## Guion de demostración (aprox. 10 minutos)

La base de datos carga un solo campus, UCC Pasto (familia andina). Las familias tropical y templada siguen en el código porque la UCC tiene sedes en otros climas; se demuestran con sus pruebas.

1. `GET /api/v1/spaces`: mostrar el campus, sus bloques A y B y los 107 espacios del plano.
2. Iniciar sesión (`POST /api/v1/auth/login`) y enviar al salón `424-A` (18 pupitres) una lectura fría con 25 personas y 1600 ppm de CO₂ con la clave de sensor (`X-API-Key`). Consultar `GET /api/v1/alerts?spaceId=424-A`: aparecen `CO2_ALTO` y `SOBRECUPO` (**Observer**).
3. `POST /api/v1/spaces/424-A/evaluations` con un token de OPERATOR y el servicio de IA apagado: la ocupación sale con `source = RESPALDO_LOCAL` (**Decorator** sobre el **Adapter**) y la recomendación es `CALEFACCION` (**Facade**, **Abstract Factory**, **Strategy**, **Builder**).
4. Repetir la evaluación con la IA encendida: `source = SERVICIO_IA`.
5. Ejecutar `ClimatePolicyFactoryTest` y `ThermalPolicyTest`: el mismo ambiente (17 °C, 60 % HR) produce calefacción en la familia andina, climatización apagada en la tropical y enfriamiento libre en la templada.
6. Crear una reserva en `402-B`, enviar lecturas vacías después de 15 minutos y mostrar `RESERVA_SIN_USO` (o ejecutar el escenario `NO_SHOW`).
7. `GET /api/v1/occupancy/live?type=BIBLIOTECA` y `GET /api/v1/dashboard/summary`.
8. Mostrar en el código las pruebas de cada patrón (tabla de arriba) y el reporte de cobertura de la CI.

## Lo que **no** se cuenta como patrón

- **`HvacCommand` y `EquipmentCommand` no son el patrón Command.** Son objetos de datos que describen una orden para el ESP32 o el Arduino; no encapsulan una operación ejecutable, no hay invocador ni historial para deshacer. Se nombran así porque son "comandos" en el sentido de mensajes MQTT.
- **Spring crea los beans una sola vez, pero eso no es Singleton GoF.** No hay una clase que controle su propia instancia; es el ciclo de vida del contenedor.
- **La arquitectura hexagonal, REST, JPA, Docker y la separación en repositorios** son decisiones de arquitectura y tecnología, no patrones GoF.
- **`TwinWriter`** (leer → decidir → guardar con revisión, repetir si hay conflicto) es control de concurrencia optimista, no un patrón GoF.
- **La fusión heurística del servicio Python** no se presenta como Strategy: el Strategy contabilizado está en las políticas Java.

## Preguntas difíciles y respuestas honestas

| Pregunta | Respuesta, con la evidencia |
|---|---|
| ¿Un anónimo puede ejecutar escenarios en producción? | No. Los escenarios están apagados por defecto y en `prod`; encendidos, exigen rol ADMIN (`AccessPolicy`) y la aplicación no arranca si conviven con actuadores reales (`OperationalSafetyCheck`). Evidencia: `CampusNexoApiIntegrationTest.simulationScenarios` (401 sin sesión, 403 para OPERATOR y VIEWER). |
| ¿Quién puede ver la ocupación? | Solo personas con sesión (VIEWER o superior), también en el WebSocket (`liveFeedRequiresSignIn`). Las rutas nuevas fallan cerrado. |
| ¿Qué impide dos reservas cruzadas enviadas a la vez? | La restricción de exclusión de PostgreSQL (V13), no solo la consulta previa. Evidencia: `concurrentReservationsCannotOverlap` lanza 6 reservas idénticas en paralelo: 1 → 201, 5 → 409. |
| ¿Qué significa `confidence_pct = 90`? | Es la estabilidad interna del filtro (derivada de su varianza), no la probabilidad de acertar. No hay datos etiquetados todavía; `campusnexo-ai-service/eval/evaluate.py` mide MAE, RMSE, sesgo y calibración cuando los haya. |
| ¿Cuál es la IA real? | YOLO (preentrenado, sin reentrenar) cuenta personas y objetos en el borde; un filtro de Kalman fusiona cámara, CO₂ y Wi-Fi (en Java para el gemelo y, con pesos fijos, en el servicio Python para el flujo de evaluación); un controlador predictivo elige el clima. Ninguno es un modelo entrenado con datos del campus. |
| Si el backend cae, ¿el operador lo nota? | Sí. En producción el dashboard no cambia solo a la simulación: muestra "Sin conexión con el servidor" y solo usa datos simulados si la persona lo elige; una franja fija avisa cuando los datos son simulados o están desactualizados. |
| ¿Dos lecturas simultáneas del mismo salón se pisan? | No. `TwinWriter` serializa las escrituras de un salón y la columna `revision` rechaza una escritura basada en un estado viejo; la decisión se recalcula. Evidencia: `TwinPipelineTest.concurrentWriteIsRecomputed` y `parallelStreamsOfOneRoomTakeTurns`. |
| ¿Por qué Builder y no un constructor? | La recomendación se arma por partes que salen de tres estrategias distintas, con cuatro datos opcionales de procedencia; el Builder concentra los valores por defecto y la validación final. Es la variante fluida para objetos inmutables, no el Builder GoF con varias representaciones. |

## Límites que se deben decir en la defensa

- Los umbrales, setpoints y la heurística de respaldo son valores de referencia; no están calibrados con instrumentos del campus.
- No hay mediciones de error de la ocupación ni de ahorro: los modelos están documentados y hay herramienta para medirlos, pero no resultados.
- En el flujo de evaluación (`/evaluations`) el estado del HVAC se **infiere** de la última recomendación. El gemelo digital sí publica órdenes al ESP32 y al Arduino por MQTT, pero no se ha probado con equipos reales.
- El backend recibe conteos agregados; no recibe ni guarda imágenes.
