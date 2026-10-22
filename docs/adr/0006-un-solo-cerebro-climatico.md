# ADR 0006: Un solo cerebro climático

- Estado: aceptada
- Fecha: 2026-10-22

## Contexto

La recomendación climática y el gemelo digital decidían el clima por caminos distintos: la recomendación usaba la banda de la familia climática y el gemelo una banda fija, y la recomendación creía que el equipo estaba apagado aunque el gemelo lo hubiera encendido. Dos respuestas distintas para el mismo salón no se pueden defender.

## Decisión

La familia climática (Abstract Factory) es la única fuente de la banda de confort, de la protección del compresor y de la estrategia térmica. El gemelo decide con `ThermalInertiaMpcDomainService` sobre esa banda y la cadena `HvacSettingChain` (sin equipo → mantenimiento → docente → compresor → familia → MPC) produce el `HvacSetting` que se comanda al ESP32. La recomendación lee el estado real del equipo desde el gemelo (`HvacStateResolver`).

## Consecuencias

- Una regla nueva es un eslabón nuevo de la cadena, probado aislado.
- Los espacios sin equipo (`hvac_installed`) quedan `SIN_CLIMATIZACION` y no acumulan ahorro.
- El orden de los eslabones es una decisión de seguridad: mantenimiento antes que el docente, compresor antes que la optimización.

## Alternativas consideradas

- Mantener dos motores sincronizados por configuración: descartado, divergen con el primer cambio.
- Un `switch` grande en el gemelo: descartado, mezcla reglas de seguridad y de optimización.
