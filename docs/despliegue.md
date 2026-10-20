# Despliegue en la nube

El requisito del curso exige el proyecto desplegado en producción. Esta guía usa **Render**, que construye el `Dockerfile` y ofrece PostgreSQL administrado. El archivo [`render.yaml`](../render.yaml) describe ambos recursos.

## 1. Backend y base de datos (Render Blueprint)

1. En Render: **New → Blueprint**, conecta este repositorio y acepta `render.yaml`.
2. Render crea `campusnexo-db` (PostgreSQL 17) y `campusnexo-backend` (Docker), con `SPRING_PROFILES_ACTIVE=prod` y las variables `DB_*` enlazadas a la base.
3. Completa las variables marcadas `sync: false`:
   - `AI_SERVICE_URL`: URL pública del servicio FastAPI (paso 2).
   - `CORS_ALLOWED_ORIGINS`: URL pública del frontend (paso 3), por ejemplo `https://campusnexo.onrender.com`.
4. `INGEST_API_KEY` se genera sola. Cópiala desde el panel para el simulador de sensores; no la subas a Git.
5. Verifica:
   ```sh
   curl https://<backend>.onrender.com/actuator/health
   curl https://<backend>.onrender.com/api/v1/spaces
   ```

Flyway aplica las migraciones al primer arranque.

> **Revisa antes de la entrega**: en el plan gratuito, los servicios se suspenden por inactividad (el primer request tarda) y las bases de datos gratuitas tienen fecha de expiración. Confirma las condiciones vigentes en tu cuenta y crea la base con margen suficiente antes del 20 o el 27 de noviembre, o usa un plan pago durante la semana de sustentación.

## 2. Servicio de IA (FastAPI)

Despliégalo como otro *Web Service* de Render desde la carpeta `ai-service` (tiene su propio Dockerfile). Pon su URL en `AI_SERVICE_URL` del backend. Si no está disponible, el backend sigue funcionando con `source = RESPALDO_LOCAL`.

## 3. Frontend (React/Vite)

Despliégalo como *Static Site* con `VITE_API_URL=https://<backend>.onrender.com/api/v1` en tiempo de build, y agrega su URL a `CORS_ALLOWED_ORIGINS` del backend.

## Alternativas

Cualquier plataforma que ejecute contenedores sirve (Railway, Fly.io, Azure Container Apps, Google Cloud Run). Variables mínimas:

| Variable | Valor |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DATABASE_URL` (JDBC) **o** `DB_HOST`, `DB_PORT`, `DB_NAME` | Conexión a PostgreSQL 15+ |
| `DATABASE_USER`/`DATABASE_PASSWORD` **o** `DB_USER`/`DB_PASSWORD` | Credenciales |
| `INGEST_API_KEY` | Secreto compartido con los sensores |
| `AI_SERVICE_URL`, `CORS_ALLOWED_ORIGINS` | URLs públicas |
| `PORT` | Lo inyecta la plataforma (por defecto, 8080) |

El *health check* está en `/actuator/health`.
