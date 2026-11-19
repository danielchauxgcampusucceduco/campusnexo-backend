# ADR 0012: Solo la cámara, centrado en la disponibilidad de espacios

- Estado: aceptada
- Fecha: 2026-11-18
- Reemplaza: [0002](0002-frontera-de-seguridad-de-actuadores.md) y [0006](0006-un-solo-cerebro-climatico.md)

## Contexto

El diseño incluía en cada salón un módulo ESP32 con sensores de temperatura, humedad, luz y corriente, más relés para apagar luces y equipos. No hay recursos para comprarlo. Solo hay presupuesto para la cámara de visión PoE: va cableada al portátil de borde, y ese portátil se conecta a la red del campus por Ethernet.

## Decisión

Se elimina todo lo que dependía del módulo:
- el control y la recomendación de clima;
- la contabilidad de energía y ahorro;
- la medición de CO₂ en el Kalman y en la fusión de la IA;
- los comandos a actuadores;
- las alertas `CO2_ALTO`, `CONSUMO_ESPACIO_VACIO` y `SENSOR_ANOMALO`.

Se conservan:
- la ocupación por cámara, con Wi-Fi opcional;
- los estados del salón, las reservas y los escenarios;
- los PCs, con su agente de latido.

El sistema se centra en la **disponibilidad**: qué espacios para estudiantes están libres, en qué horario. `GET /api/v1/disponibilidad` cruza:
- el horario académico,
- las reservas,
- y, en la hora actual, la cámara.

Una clase sin nadie a los 15 minutos genera `HORARIO_SIN_USO` para el administrador. Solo se siguen los espacios de estudiantes (`SpaceType.accesibleParaEstudiantes()`); las oficinas siguen en el catálogo sin seguimiento.

La migración V16 borra las tablas y columnas de clima, y limpia `space_twin_states`, porque cambia la forma del gemelo. V17 agrega las dos salas de la hemeroteca.

## Consecuencias

- Ya no hay actuadores, así que desaparece el riesgo que motivaba el ADR 0002.
- Quedan nueve patrones; salen Abstract Factory y Chain of Responsibility, que solo existían para el clima.
- Volver a medir el ambiente requiere el módulo y un adaptador de entrada nuevo; el núcleo no tiene que cambiar.

## Alternativas consideradas

- Mantener el clima con datos emulados: descartado, porque mostraría cifras que ningún dispositivo mide.
