# ADR 0010: Historial del gemelo en MongoDB

- Estado: aceptada
- Fecha: 2026-10-24

## Contexto

El pronóstico de ocupación y la evaluación de la IA necesitan el historial de cada salón: un documento por cambio del gemelo, que crece sin límite fijo y cuya forma evoluciona (escenarios, roles, banderas). El estado actual del gemelo y la telemetría deben seguir siendo transaccionales (ADR 0007).

## Decisión

- PostgreSQL (Neon) sigue siendo la fuente de verdad transaccional: catálogo, gemelo, lecturas, reservas, personas, activos, alertas.
- MongoDB (Atlas) guarda dos colecciones de solo inserción: `historial_gemelo` (un documento plano por cambio) y `trazas` (caducan a los 30 días). Las escribe `MongoTwinHistoryAdapter`, que es otro seguidor del puerto `TwinChangeNotifierPort` (Observer), después del commit y como mejor esfuerzo.
- Se usa el driver oficial sin Spring Data; sin `MONGODB_URI` no hay historial y el sistema funciona igual.

## Consecuencias

- Si MongoDB cae, el gemelo y el tablero siguen; ese tramo falta en el historial y se registra en el log.
- El pronóstico responde `409 history_not_available` mientras un salón no tenga historial.

## Alternativas consideradas

- Mover el estado del gemelo a MongoDB: descartado, rompería la transacción con la lectura.
- Una tabla de historial en PostgreSQL: posible, pero el curso pide una base no relacional y el historial es el uso que mejor le corresponde.
