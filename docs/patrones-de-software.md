# Patrones de software en CampusNexo Backend

Esta selección aplica las categorías estudiadas en el curso y se limita a patrones que tienen participantes concretos y una colaboración visible en el flujo del backend. La arquitectura hexagonal no se suma al conteo de patrones GoF.

## Resumen

| Categoría | Patrón | Necesidad en el caso | Participantes del código |
|---|---|---|---|
| Creacional | Abstract Factory | Mantener juntas las políticas compatibles con el perfil climático de cada sede. | `ClimatePolicyFactory`; `AndeanClimatePolicyFactory`, `TropicalClimatePolicyFactory`, `TemplatedClimatePolicyFactory`; productos `ThermalPolicy`, `VentilationPolicy`, `LightingPolicy`. |
| Creacional | Builder | Crear una recomendación inmutable sin permitir que falten decisiones obligatorias. | `ClimateRecommendation.Builder` y `ClimateRecommendation`. |
| Estructural | Adapter | Integrar el contrato Java con el endpoint HTTP/Pydantic del microservicio Python. | Puerto `OccupancyEstimator` y adaptador `HttpOccupancyEstimatorAdapter`. |
| De comportamiento | Observer | Reaccionar a una lectura persistida con alertas independientes y extensibles. | `TelemetryEventPublisher`, evento `TelemetryRecorded` y `TelemetryObserver`; observadores `AirQualityAlertObserver`, `CapacityAlertObserver`, `EnergyWasteAlertObserver`. |
| De comportamiento | Strategy | Cambiar algoritmos de decisión climática mediante contratos comunes. | `ThermalPolicy`, `VentilationPolicy`, `LightingPolicy`; implementaciones de políticas y familias elegidas por perfil. |

## Abstract Factory y Strategy

`ClimateRecommendationService` obtiene el perfil de la sede del catálogo. Busca la fábrica correspondiente y solicita tres estrategias relacionadas. Así, la sede andina, tropical o templada selecciona una familia coherente de algoritmos sin llenar el caso de uso de condiciones por cada decisión.

Cada interfaz de política también representa el contrato de una Strategy: el caso de uso ejecuta la política por interfaz y no necesita conocer cómo se calcula. La fábrica crea la familia; la estrategia encapsula el algoritmo de una decisión.

Flujo principal:

```text
POST /climate/recommendations
  → ClimateRecommendationService
  → consulta de espacio y perfil
  → ClimatePolicyFactory del perfil
  → ThermalPolicy + VentilationPolicy + LightingPolicy
  → ClimateRecommendation.Builder
  → ClimateRecommendationRepository
```

Los setpoints, umbrales de CO₂ y niveles de luz son parámetros ilustrativos para el prototipo; deben validarse y calibrarse con datos reales y especialistas antes de una operación real.

## Builder

`ClimateRecommendation.Builder` permite completar el contexto gradualmente. `build()` rechaza una recomendación si falta el espacio, el perfil, el instante o alguna decisión requerida. El objeto final es un `record` inmutable.

## Adapter

`OccupancyEstimationService` depende del puerto de dominio `OccupancyEstimator`. `HttpOccupancyEstimatorAdapter` traduce los valores a las propiedades `snake_case` del servicio Python y su respuesta al modelo Java. Si el endpoint no está disponible, el adaptador transforma el error de transporte en `AiServiceUnavailableException`; el dominio y el caso de uso no importan clases HTTP.

La interfaz entre servicios transmite conteos agregados. No se transfieren ni almacenan imágenes en este backend.

## Observer

Después de guardar telemetría, `TelemetryService` publica `TelemetryRecorded`. `TelemetryEventPublisher` recorre los consumidores registrados como `TelemetryObserver`. En el prototipo, los observadores reportan condiciones de aire, capacidad y consumo con logs; no operan equipos. Un fallo de observación se registra sin convertir una lectura ya guardada en una respuesta HTTP fallida.

El umbral de energía residual de `EnergyWasteAlertObserver` es un valor de demostración, no una regla institucional validada.

## Guion de demostración del patrón

1. Consultar `/api/v1/spaces` y seleccionar un espacio de cada perfil climático disponible.
2. Enviar recomendaciones con valores que activen y desactiven HVAC, ventilación e iluminación; comparar las explicaciones.
3. Enviar una lectura de telemetría que exceda capacidad y umbral de CO₂; revisar los logs y consultar la medición guardada.
4. Consultar una estimación con el microservicio Python disponible y volver a intentar con el servicio desconectado para observar el `503` traducido por el Adapter.
5. Explicar los contratos, responsabilidades y razón de cada patrón; no presentar el nombre de una clase como evidencia suficiente por sí sola.

## Límites para la defensa académica

- El endpoint de cámara no existe en este backend: recibe el conteo ya agregado por IA.
- Las reglas de climatización y los umbrales no están calibrados.
- El Observer actual alerta por logging, sin correo, cola de eventos ni actuadores.
- Los cinco patrones tienen implementación; la demostración en vivo y la explicación conceptual todavía deben prepararse para la sustentación.
