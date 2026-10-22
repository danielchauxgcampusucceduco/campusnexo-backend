# ADR 0008: Demostración aislada del campus real

- Estado: aceptada
- Fecha: 2026-10-22

## Contexto

Los escenarios de sustentación preparaban salones escribiendo el gemelo directamente y el reinicio afectaba a todo el campus. Una demostración no puede borrar reservas de personas ni dejar salones en estados que ningún sensor produjo.

## Decisión

Los escenarios solo tocan los espacios con `simulation_allowed` (11) y usan exclusivamente los puertos de entrada de los dispositivos y las personas (regla ArchUnit). Reproducen frames reales en el tiempo, crean reservas con origen `SIMULACION` y comprueban sus precondiciones antes de escribir (`409 simulation_blocked_by_reservation`). Con `SIMULATION_ENABLED=false` el controlador no existe y el arranque falla si la simulación convive con actuadores reales.

## Consecuencias

- Lo que muestra la demostración es lo que haría el sistema con esos datos reales.
- Las reservas de personas nunca se borran ni se pisan.

## Alternativas consideradas

- Base de datos separada para la demo: descartado, dejaría de demostrar el sistema real.
