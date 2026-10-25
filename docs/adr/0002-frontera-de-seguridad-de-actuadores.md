# ADR 0002: Actuadores físicos solo por decisión explícita

- Estado: aceptada
- Fecha: 2026-10-21

## Contexto

El gemelo digital calcula órdenes para el clima (ESP32) y para luces y computadores (Arduino). En la nube no hay equipos reales conectados. Un entorno académico no debe accionar equipos reales por accidente.

## Decisión

La aplicación usa adaptadores de registro como comportamiento seguro por defecto: las órdenes llegan al log y al tablero. Los adaptadores MQTT de actuadores solo se crean cuando MQTT está habilitado y `ACTUATORS_MODE=mqtt`. La validación de arranque rechaza la seguridad en modo desarrollo junto con actuadores reales.

## Consecuencias

- Las pruebas recorren el flujo completo sin hardware y sin riesgo.
- La activación de actuadores requiere configuración explícita; la nube mantiene `ACTUATORS_MODE=log`.
- Un despliegue que active el modo MQTT debe revisar credenciales, tópicos y equipos antes de arrancar.

## Alternativas consideradas

- Habilitar MQTT para todo el gemelo: descartado porque cualquier prueba accionaría equipos.
- Confiar en que nadie pulse un control: descartado; la incompatibilidad se valida al iniciar.
