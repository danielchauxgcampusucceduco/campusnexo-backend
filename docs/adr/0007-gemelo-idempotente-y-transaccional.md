# ADR 0007: Gemelo idempotente y transaccional

- Estado: aceptada
- Fecha: 2026-10-22

## Contexto

MQTT con QoS 1 entrega al menos una vez y puede desordenar mensajes; varias instancias pueden procesar el mismo salón. Un frame repetido no puede contar dos veces el tiempo vacío ni crear dos lecturas, y el gemelo y su lectura no pueden quedar a medias.

## Decisión

- El gemelo guarda la hora de captura de la última lectura de cada fuente; una lectura repetida o vieja no cambia nada y se cuenta como descartada.
- `ReadingAdmission` valida la lectura antes de cualquier efecto.
- `TwinWriter` serializa por salón dentro de la instancia, ejecuta cada intento en una transacción (`UnitOfWork`) y usa la columna `revision` para rechazar escrituras basadas en un estado viejo (hasta 3 intentos).
- La lectura se guarda en la misma transacción; las alertas y los comandos se emiten al confirmar.
- `UNIQUE (space_id, recorded_at)` en `telemetry_stream`; MQTT con confirmación manual después de procesar y sesión persistente.

## Consecuencias

- Reprocesar es seguro; perder el proceso a mitad de un mensaje provoca una reentrega, no una pérdida.
- El motor de decisión es puro: recibe el contexto ya leído (reservas, regiones de pupitre).

## Alternativas consideradas

- Bloqueo pesimista en base de datos por cada frame: descartado por latencia.
- Deduplicar por id de mensaje MQTT: descartado, el id no sobrevive a reinicios del dispositivo.
