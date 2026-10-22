# ADR 0004: Usar paginación keyset en los listados cronológicos

- Estado: aceptada
- Fecha: 2026-10-21

## Contexto

Telemetría y alertas crecen con el uso. La paginación por desplazamiento puede repetir u omitir resultados cuando se insertan filas mientras el cliente recorre páginas.

## Decisión

Los listados cronológicos usan un cursor opaco formado por instante y clave de desempate, ordenados de más reciente a más antiguo. Las respuestas siguen siendo arreglos JSON y entregan el cursor siguiente en X-Next-Cursor cuando la página está llena.

## Consecuencias

- Las inserciones nuevas no desplazan las filas de páginas ya recorridas.
- El cursor preserva orden determinista cuando varias filas comparten instante.
- El formato del cursor es un detalle del servidor; se valida al decodificar.
- Los clientes deben reutilizar el encabezado, no construir cursores manualmente.

## Alternativas consideradas

- Parámetros page/offset: descartados para flujos con inserciones frecuentes.
- Exponer una fecha sola: descartada porque no distingue filas con la misma marca temporal.
