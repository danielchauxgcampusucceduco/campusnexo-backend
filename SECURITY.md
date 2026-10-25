# Seguridad

Proyecto académico en estado de prototipo. No almacenes datos personales, imágenes, credenciales reales ni información interna de la universidad en issues, logs, commits o solicitudes de cambio.

## Controles implementados

- **Inicio de sesión con roles.** `POST /api/v1/auth/login` verifica la contraseña (PBKDF2-HMAC-SHA256, 210 000 iteraciones) y entrega un token firmado con HMAC-SHA256, con vencimiento (`APP_TOKEN_TTL`, 8 h por defecto). Un token deja de valer si la persona se elimina o cambia de rol en `APP_USERS`. Roles:

  | Rol | Puede |
  |---|---|
  | `VIEWER` (jurado) | Leer todo (`GET /api/**`) y recibir el canal en vivo |
  | `OPERATOR` (personal) | Lo anterior, más reservas, evaluaciones, estimaciones y controles manuales |
  | `ADMIN` | Todo, incluida la administración (`/api/v1/admin/**`): personas, inventario y herramientas de IA |
  | `SENSOR` (claves `APP_SENSOR_KEYS`) | Solo enviar lo que reportan los dispositivos: lecturas, latidos de PC, check-in del kiosco, descubrimiento, emparejamiento y auditoría de puestos |

- **Cada ruta tiene un permiso** en una sola tabla (`infrastructure/security/AccessPolicy`); una ruta que no está en la tabla falla cerrado (lectura → `VIEWER`, cualquier otro método → `ADMIN`). Solo son públicas `/actuator/health`, `/api/v1/auth/login` y `/api/v1/auth/session`.
- **Canal en vivo autenticado.** El `CONNECT` de STOMP debe traer `Authorization: Bearer <token>`; solo se permite suscribirse a `/topic/twin` y `/topic/traces`, y no se aceptan mensajes del cliente.
- **Falla cerrado por defecto.** `app.security.mode=enforced` es el valor por defecto y el perfil `prod` lo fija sin poder cambiarlo; sin `APP_TOKEN_SECRET` (32+ caracteres) o sin un `ADMIN`, la aplicación no arranca. El modo `development` (solicitudes sin credenciales actúan como administrador local) solo se activa a propósito, y lo anuncia en el log.
- **Administración por red.** `/api/v1/admin/**` exige rol `ADMIN` **y** venir de `ADMIN_ALLOWED_NETWORKS` (la IP fija de la VPN WireGuard en AWS, ADR 0011). La IP del cliente es la última entrada de `X-Forwarded-For` agregada por un proxy de confianza (`TRUSTED_PROXIES`); un `X-Forwarded-For` falsificado no cambia nada. Con `APP_ENVIRONMENT=production` el backend no arranca sin `ADMIN_ALLOWED_NETWORKS`: olvidar la variable no deja la administración abierta.
- **Personas sin biometría.** Docentes y estudiantes se guardan con código, nombre y adscripción; se identifican por su código en el kiosco y la cámara solo envía posiciones (ADR 0008). Leer personas exige `ADMIN`.
- **Una clave por dispositivo.** Cada cámara, Arduino, agente de PC o kiosco tiene la suya, para revocarla sin tocar las demás.
- **Límites y auditoría.** Intentos de inicio de sesión por IP (`LOGIN_ATTEMPTS_PER_MINUTE`) y escrituras por persona e IP (`WRITES_PER_MINUTE`) → `429`. Cada operación y cada rechazo quedan en el log `campusnexo.audit` (usuario, rol, ruta, estado, IP), sin contraseñas ni tokens.
- Comparaciones de claves y firmas en tiempo constante; verificación de contraseña con el mismo costo aunque el usuario no exista.
- El dashboard no contiene claves: cada persona inicia sesión y su token vive en `sessionStorage`.
- El perfil `prod` no tiene credenciales de base de datos por defecto; los errores no exponen mensajes internos; CORS limitado a `CORS_ALLOWED_ORIGINS` (también en respuestas 401/403); la imagen Docker se ejecuta con un usuario sin privilegios.

## Pendiente para un uso institucional

- Identidad institucional (OIDC/SSO) en lugar de usuarios en configuración, y revocación inmediata de sesiones (hoy un token vale hasta que vence, salvo que se elimine el usuario).
- Autenticación del broker MQTT (usuario/contraseña o certificados por dispositivo) y TLS; hoy el broker local de Compose es anónimo.
- Límites de tasa compartidos entre instancias (hoy son en memoria, por instancia).
- Retención de datos, monitoreo y una evaluación de impacto para la cámara (finalidad, base legal, señalización).

Si encuentras una vulnerabilidad, repórtala mediante un GitHub Security Advisory privado en este repositorio; no publiques detalles explotables en un issue abierto.
