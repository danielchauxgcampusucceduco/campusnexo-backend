# ADR 0003: Aislar la estimación remota de ocupación y conservar un respaldo local

- Estado: aceptada
- Fecha: 2026-10-21

## Contexto

El servicio Python puede estar fuera de línea o responder con datos inválidos. La evaluación de un espacio debe seguir disponible y la API debe distinguir una estimación de una medición confirmada.

## Decisión

El dominio depende del puerto OccupancyEstimator. El adaptador HTTP traduce el contrato Python; decoradores de aplicación añaden reintentos acotados y respaldo heurístico local. Las respuestas conservan procedencia y limitaciones de la estimación. confidence_pct representa estabilidad interna, no probabilidad de acierto.

## Consecuencias

- El núcleo puede probar el proveedor remoto, los reintentos y el respaldo por separado.
- La caída de Python no detiene el flujo de evaluación.
- El cliente recibe una estimación utilizable, pero no debe presentarla como conteo real.
- La precisión solo se puede afirmar después de medir errores con observaciones etiquetadas.

## Alternativas consideradas

- Hacer que cada caso de uso llame directamente al servicio HTTP: descartado por acoplamiento y por duplicar manejo de fallos.
- Fallar toda evaluación cuando Python no responde: descartado porque elimina una capacidad que el dominio puede aproximar localmente.
- Tratar confidence_pct como exactitud: descartado porque no está calibrado con resultados reales.
