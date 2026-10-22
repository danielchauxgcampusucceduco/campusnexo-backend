# Despliegue en la nube

El requisito del curso exige el proyecto desplegado en producción. El archivo [`render.yaml`](../render.yaml) describe **todo el sistema** en Render: base de datos, backend, servicio de IA y dashboard. Esta guía lo despliega y lo verifica paso a paso.

| Recurso | Origen | Tipo | Comprobación |
|---|---|---|---|
| `campusnexo-db` | — | PostgreSQL 17 | El backend arranca (Flyway aplica V1–V13) |
| `campusnexo-backend` | este repositorio | Docker, perfil `prod` | `GET /actuator/health` → `UP` |
| `campusnexo-ai` | `campusnexo-ai-service` | Docker | `GET /health` → `ok` |
| `campusnexo-dashboard` | `campusnexo-frontend` | sitio estático | La página pide iniciar sesión |

## 1. Antes de empezar

1. Los tres repositorios deben existir en GitHub y Render debe tener acceso a ellos: `campusnexo-backend`, `campusnexo-frontend` y `campusnexo-ai-service`.
2. Genera las cuentas de las personas (cada contraseña de 10+ caracteres):

   ```sh
   python3 tools/auth/hash_password.py     # una vez por persona; copia el hash
   ```

   Arma `APP_USERS` con `nombre:ROL:hash` separados por comas, por ejemplo
   `daniel:ADMIN:<hash>,jurado:VIEWER:<hash>,personal:OPERATOR:<hash>`. Guarda las contraseñas fuera del repositorio.

## 2. Crear los recursos

En Render: **New → Blueprint**, elige el repositorio `campusnexo-backend` y acepta `render.yaml`. Render crea los cuatro recursos y genera `APP_TOKEN_SECRET`, `APP_SENSOR_KEYS` y la clave `AI_API_KEY` del servicio de IA, que el backend toma del mismo valor (`fromService`): nadie la copia a mano. El blueprint fija `APP_ENVIRONMENT=production`, con el que el backend se niega a arrancar con la seguridad de desarrollo.

**Orden de despliegue.** El servicio de IA nuevo exige `area_sqm`, `capacity` y `X-API-Key`; despliega primero la IA y luego el backend. Mientras la IA no responde, el backend usa su respaldo local (mismo modelo de fusión) y lo cuenta en `/actuator/prometheus`.

## 3. Completar las variables (`sync: false`)

| Servicio | Variable | Valor |
|---|---|---|
| backend | `APP_USERS` | la lista del paso 1 |
| backend | `AI_SERVICE_URL` | URL pública de `campusnexo-ai` (p. ej. `https://campusnexo-ai.onrender.com`) |
| backend | `CORS_ALLOWED_ORIGINS` | URL pública de `campusnexo-dashboard` |
| dashboard | `VITE_API_URL` | `https://<backend>.onrender.com/api/v1` |
| dashboard | `VITE_WS_URL` | `wss://<backend>.onrender.com/ws` |

Después de guardarlas, **redespliega** el backend y el dashboard (las variables del dashboard se incrustan al compilar).

## 4. Verificar (y guardar la evidencia)

```sh
B=https://<backend>.onrender.com
curl -s $B/actuator/health                                   # {"status":"UP"}
curl -s -o /dev/null -w '%{http_code}\n' $B/api/v1/spaces    # 401: sin sesión no hay datos
curl -s -o /dev/null -w '%{http_code}\n' -X POST $B/api/v1/simulation/REINICIAR   # 401
TOKEN=$(curl -s -X POST $B/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"jurado","password":"<contraseña>"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
curl -s -H "Authorization: Bearer $TOKEN" $B/api/v1/spaces | python3 -c 'import sys,json;print(len(json.load(sys.stdin)))'  # 107
curl -s -o /dev/null -w '%{http_code}\n' -X POST -H "Authorization: Bearer $TOKEN" $B/api/v1/simulation/REINICIAR  # 403 (viewer)
curl -s https://<ai>.onrender.com/health                     # {"status":"ok",...}
curl -s -o /dev/null -w '%{http_code}\n' -X POST https://<ai>.onrender.com/v1/occupancy/estimate   # 401 sin X-API-Key
```

Abre el dashboard, inicia sesión y comprueba que la barra superior dice **Servidor real** y **En vivo**. Anota en el documento de entrega las tres URL, la fecha y el resultado de estos comandos.

## 5. Durante la sustentación

- Para usar los escenarios, pon `SIMULATION_ENABLED=true` en el backend y redespliega; solo un **ADMIN** puede ejecutarlos y las órdenes van al log (`ACTUATORS_MODE=log`). Vuelve a `false` al terminar.
- El backend se niega a arrancar si alguien pone `SIMULATION_ENABLED=true` junto con `ACTUATORS_MODE=mqtt`.
- En el plan gratuito los servicios se duermen: abre las tres URL unos minutos antes. La base de datos gratuita tiene fecha de expiración: créala con margen antes del 20 o el 27 de noviembre.

## Alternativas

Cualquier plataforma que ejecute contenedores sirve (Railway, Fly.io, Azure Container Apps, Google Cloud Run). Variables mínimas del backend:

| Variable | Valor |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DATABASE_URL` (JDBC) **o** `DB_HOST`, `DB_PORT`, `DB_NAME` | PostgreSQL 15+ con la extensión `btree_gist` disponible (V13) |
| `DATABASE_USER`/`DATABASE_PASSWORD` **o** `DB_USER`/`DB_PASSWORD` | Credenciales |
| `APP_USERS`, `APP_TOKEN_SECRET`, `APP_SENSOR_KEYS` | Personas, secreto de tokens (32+), claves de sensores |
| `AI_SERVICE_URL`, `CORS_ALLOWED_ORIGINS` | URLs públicas |
| `AI_API_KEY` | La misma clave (24+ caracteres) en el backend y en el servicio de IA |
| `APP_ENVIRONMENT` | `production`: impide arrancar con la seguridad de desarrollo |
| `MQTT_INSTANCE_ID` | Identificador estable por instancia (sesión MQTT persistente) |
| `FORWARD_HEADERS_STRATEGY` | `framework` detrás de un proxy |
| `PORT` | Lo inyecta la plataforma (por defecto, 8080) |
