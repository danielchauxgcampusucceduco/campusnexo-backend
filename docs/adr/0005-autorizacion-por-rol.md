# ADR 0005: Aplicar autorización por rol en el servidor

- Estado: aceptada
- Fecha: 2026-10-21

## Contexto

El dashboard adapta las acciones visibles al rol de la persona, pero una interfaz cliente no es una frontera de seguridad. Las rutas REST y el feed WebSocket deben aplicar la misma política aunque se llamen fuera del dashboard.

## Decisión

Las sesiones se autentican en el backend y cada ruta se declara pública, autenticada o limitada por rol en AccessPolicy. La autorización del feed en vivo se valida durante el handshake. El frontend usa la identidad de sesión para presentar capacidades, pero el backend sigue siendo la autoridad.

## Consecuencias

- Ocultar un botón nunca sustituye la autorización del endpoint.
- Los cambios de rutas requieren actualizar AccessPolicy y sus pruebas.
- Los contratos de sesión informan al dashboard de su rol sin conceder permisos por sí solos.
- Las pruebas de integración deben cubrir acceso anónimo y roles permitidos y denegados.

## Alternativas consideradas

- Autorizar solo en React: descartado porque cualquier cliente podría omitir esas comprobaciones.
- Usar una única llave compartida en el navegador: descartado porque expondría credenciales reutilizables.
