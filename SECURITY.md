# Seguridad

Este repositorio es un proyecto académico en estado de prototipo. No almacenes datos personales, imágenes, credenciales reales ni información interna de la universidad en issues, logs, commits o solicitudes de cambio.

## Controles implementados

- `X-API-Key` (variable `INGEST_API_KEY`) protege la ingesta de telemetría y la creación de reservas, y es obligatoria con el perfil `prod`. La comparación es en tiempo constante.
- El perfil `prod` no tiene credenciales de base de datos por defecto.
- Los errores no exponen mensajes internos (`internal_error` genérico; el detalle va solo a los logs).
- CORS limitado a los orígenes configurados en `CORS_ALLOWED_ORIGINS`.
- La imagen Docker se ejecuta con un usuario sin privilegios.

## Pendiente para un uso institucional

Autenticación de usuarios con roles (por ejemplo, OIDC institucional), TLS de extremo a extremo, rotación de secretos, límites de tasa, política de retención, monitoreo y una evaluación de impacto para la cámara (finalidad, base legal, señalización).

Si encuentras una vulnerabilidad, repórtala mediante un GitHub Security Advisory privado en este repositorio; no publiques detalles explotables en un issue abierto.
