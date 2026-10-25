# ADR 0001: Mantener el núcleo con arquitectura hexagonal

- Estado: aceptada
- Fecha: 2026-10-21

## Contexto

CampusNexo recibe datos desde REST, MQTT y WebSocket, persiste en PostgreSQL e integra un servicio de estimación en Python. Las reglas de reservas, ocupación, confort y ahorro deben poder probarse sin iniciar esos sistemas externos.

## Decisión

El dominio y los casos de uso no dependen de Spring, JPA, MQTT ni HTTP. Los puertos describen lo que la aplicación necesita; los adaptadores de entrada y salida traducen los protocolos y modelos externos. ArchUnit verifica las direcciones de dependencia en cada build.

## Consecuencias

- Las reglas centrales se prueban con JUnit sin contexto de Spring.
- El mismo caso de uso puede recibir adaptadores reales o dobles de prueba.
- Los cambios de protocolo se concentran en adaptadores, aunque implican mappers explícitos.
- Los contratos de los puertos deben evolucionar con los casos de uso que los consumen.

## Alternativas consideradas

- Poner reglas en controladores y servicios Spring: descartado porque acopla comportamiento al transporte.
- Usar entidades JPA como modelo del dominio: descartado para evitar que el esquema de persistencia dicte las reglas.
