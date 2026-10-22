# ADR 0002: Separar las demostraciones de los actuadores físicos

- Estado: aceptada
- Fecha: 2026-10-21

## Contexto

El gemelo digital calcula decisiones para HVAC e iluminación. El proyecto también incluye escenarios de demostración que modifican estados para explicar patrones, pero el entorno académico no debe accionar equipos reales de forma accidental.

## Decisión

La aplicación usa adaptadores de registro como comportamiento seguro por defecto. Los adaptadores MQTT de actuadores solo se crean cuando MQTT está habilitado y la variable ACTUATORS_MODE tiene el valor mqtt. La validación de arranque rechaza la combinación de escenarios de simulación y actuadores MQTT reales.

## Consecuencias

- Las pruebas y demostraciones locales pueden recorrer el flujo sin hardware.
- La activación de actuadores requiere configuración explícita.
- Los escenarios no se presentan como operación física y la nube mantiene ACTUATORS_MODE=log.
- Un despliegue que active el modo MQTT debe revisar credenciales, tópicos y equipos antes de arrancar.

## Alternativas consideradas

- Habilitar MQTT para todo el gemelo: descartado porque mezclaría demostraciones y operación real.
- Confiar únicamente en que el operador no pulse un control: descartado; la incompatibilidad se valida al iniciar.
