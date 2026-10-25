# ADR 0009: Puestos de trabajo registrados por la cámara y confirmados por la administración

- Estado: aceptada
- Fecha: 2026-10-24

## Contexto

Las salas de cómputo tienen decenas de puestos (CPU, silla y mesa). Antes solo existía un conteo de computadores por salón: no se sabía cuál quedaba encendido ni qué faltaba. Darlos de alta a mano es lento y se desactualiza.

## Decisión

- `PuestoDeTrabajo` es un Composite de `Activo` (`Cpu`, `Silla`, `Mesa`) ubicado en un asiento de la rejilla; su código es la región de cámara del asiento, así la cámara y el inventario se encuentran sin traducciones.
- La cámara propone los puestos con el salón vacío (el monitor es el ancla; la silla y la mesa más cercanas lo completan). Cada PC se empareja con su puesto mostrando un patrón en su propia ranura de tiempo, que el agente conoce por la respuesta a su latido. La cámara reporta en qué puesto vio el patrón y a qué hora.
- La propuesta queda pendiente hasta que un ADMIN la confirma; una nueva corrida solo propone las diferencias con lo registrado.
- Después, la auditoría compara lo que se ve con lo registrado (`ACTIVO_FALTANTE`), y el latido de cada CPU con el salón vacío levanta `CPU_ENCENDIDA_SIN_USUARIO`.

## Consecuencias

- El inventario de 301-A no se siembra: nace del registro por cámara en la piloto.
- La cámara tiene que ver las pantallas: se monta en la esquina trasera, frente a los monitores.
- La torre bajo la mesa no se ve: su estado lo da el agente, no la imagen.

## Alternativas consideradas

- Inventario manual solamente: descartado por costo y desactualización.
- Emparejar CPU por la red (MAC, switch): depende de la infraestructura de cada sala; el patrón en pantalla solo necesita la cámara que ya está.
