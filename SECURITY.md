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
- **Canal en vivo autenticado.** El `CONNECT` de STOMP debe traer `Authorization: Bearer <token>`; solo se permite suscribirse a `/topic/twin` y `/topic/traces`, y no se aceptan mensajes del cliente. Cada `SUBSCRIBE` vuelve a comprobar el rol que exige el tema y que la sesión no haya vencido: un socket abierto no sobrevive a su token.
- **Falla cerrado por defecto.** `app.security.mode=enforced` es el valor por defecto y el perfil `prod` lo fija sin poder cambiarlo; sin `APP_TOKEN_SECRET` (32+ caracteres) o sin un `ADMIN`, la aplicación no arranca. El modo `development` (solicitudes sin credenciales actúan como administrador local) solo se activa a propósito, y lo anuncia en el log.
- **Administración por red.** `/api/v1/admin/**` exige rol `ADMIN` **y** venir de `ADMIN_ALLOWED_NETWORKS` (la IP fija de la VPN WireGuard en AWS, ADR 0011). La IP del cliente es la última entrada de `X-Forwarded-For` agregada por un proxy de confianza (`TRUSTED_PROXIES`); un `X-Forwarded-For` falsificado no cambia nada. Con `APP_ENVIRONMENT=production` el backend no arranca sin `ADMIN_ALLOWED_NETWORKS`: olvidar la variable no deja la administración abierta.
- **Personas sin biometría.** Docentes y estudiantes se guardan con código, nombre y adscripción; se identifican por su código en el kiosco y la cámara solo envía posiciones (ADR 0008). Leer personas exige `ADMIN`.
- **Una clave por dispositivo.** Cada cámara, Arduino, agente de PC o kiosco tiene la suya, para revocarla sin tocar las demás.
- **Límites y auditoría.** Intentos de inicio de sesión por IP (`LOGIN_ATTEMPTS_PER_MINUTE`) y escrituras por persona e IP (`WRITES_PER_MINUTE`) → `429`. Cada operación y cada rechazo quedan en el log `campusnexo.audit` (usuario, rol, ruta, estado, IP), sin contraseñas ni tokens.
- Comparaciones de claves y firmas en tiempo constante; verificación de contraseña con el mismo costo aunque el usuario no exista.
- El dashboard no contiene claves: cada persona inicia sesión y su token vive en `localStorage` con su vencimiento, para que un enlace abierto en otra pestaña conserve la sesión; uno vencido se descarta al leerlo. El tablero se sirve con `Content-Security-Policy` (scripts solo del propio sitio, conexiones solo al backend, `frame-ancestors 'none'`), `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Permissions-Policy` y `nosniff` (`render.yaml`, `vercel.json`).
- El perfil `prod` no tiene credenciales de base de datos por defecto; los errores no exponen mensajes internos; CORS limitado a `CORS_ALLOWED_ORIGINS` (también en respuestas 401/403); la imagen Docker se ejecuta con un usuario sin privilegios.

## Decisiones

- **Token en `localStorage` y no en una cookie `HttpOnly`.** La cookie `HttpOnly; Secure; SameSite=Strict` es lo más seguro, pero el tablero (`campusnexo-dashboard.onrender.com`) y el backend (`campusnexo-backend.onrender.com`) son sitios distintos para el navegador: `onrender.com` está en la Public Suffix List. Una cookie `SameSite=Strict` no viajaría en las peticiones del tablero, y una `SameSite=None` la bloquean las restricciones a cookies de terceros de los navegadores, así que el inicio de sesión dejaría de funcionar. Mientras sigan en dominios distintos, la segunda barrera es la CSP, que impide cargar scripts ajenos. Con un dominio propio que sirva el tablero y la API desde el mismo sitio (por ejemplo `campusnexo.ucc.edu.co` y `api.campusnexo.ucc.edu.co`), el token puede pasar a una cookie `HttpOnly` emitida por `/auth/login`.
- **Espacios sensibles.** «Archivo Historias Clínicas» y «Unidad de Cuidado Crítico» aparecen con su ocupación en vivo para todo rol con sesión. El gemelo solo publica conteos y estados del salón, nunca identidades, imágenes ni posiciones de una persona identificable, y ningún rol sin sesión ve nada. Si la universidad decide que esos conteos tampoco deben verse, el paso es excluir esos espacios de `/topic/twin` y de `/api/v1/twin/**` para `VIEWER`: es una decisión de la institución, no un defecto técnico.

## Pendiente para un uso institucional

- Identidad institucional (OIDC/SSO) en lugar de usuarios en configuración, y revocación inmediata de sesiones (hoy un token vale hasta que vence, salvo que se elimine el usuario).
- Autenticación del broker MQTT (usuario/contraseña o certificados por dispositivo) y TLS; hoy el broker local de Compose es anónimo.
- Límites de tasa compartidos entre instancias (hoy son en memoria, por instancia).
- Retención de datos, monitoreo y una evaluación de impacto para la cámara (finalidad, base legal, señalización).

Si encuentras una vulnerabilidad, repórtala mediante un GitHub Security Advisory privado en este repositorio; no publiques detalles explotables en un issue abierto.
