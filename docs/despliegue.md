# Despliegue

| Pieza | Dónde | Plan |
|---|---|---|
| Backend Java | Render (`render.yaml`, Docker, perfil `prod`) | free; se duerme a los 15 min sin tráfico |
| Servicio de IA | Render (`render.yaml`, repo `campusnexo-ai-service`) | free |
| Frontend | Vercel (repo `campusnexo-frontend`) | Hobby |
| PostgreSQL 17 | Neon | free, sin vencimiento |
| MongoDB 8 | MongoDB Atlas M0 (replica set de 3 nodos) | free, sin vencimiento |
| VPN de administración | AWS: `deploy/aws/vpn-administracion.yaml` | EC2 t4g.nano con IP elástica, unos US$4–5 al mes |

El Postgres gratuito de Render vence a los 30 días; por eso las bases de datos van en Neon y Atlas.

## 1. Bases de datos

1. **Neon:** crea el proyecto `campusnexo` (PostgreSQL 17) y copia la cadena JDBC (`jdbc:postgresql://<host>/<db>?sslmode=require`), el usuario y la contraseña. Flyway crea el esquema y siembra V1–V11 en el primer arranque del backend.
2. **Atlas:**
   - Crea un clúster M0 y un usuario de base de datos solo con lectura y escritura sobre la base `campusnexo`.
   - En *Network Access* agrega las IP de salida de Render (panel del servicio → *Outbound*) y la IP de la VPN.
   - Copia la URI `mongodb+srv://…`.

## 2. VPN de administración (AWS)

```sh
aws cloudformation deploy --template-file deploy/aws/vpn-administracion.yaml --stack-name campusnexo-vpn \
    --capabilities CAPABILITY_IAM --parameter-overrides Administradores=3
aws cloudformation describe-stacks --stack-name campusnexo-vpn --query "Stacks[0].Outputs"
```

- La salida `AdminAllowedNetworks` (p. ej. `203.0.113.7/32`) va en `ADMIN_ALLOWED_NETWORKS` del backend.
- Cada administrador obtiene su configuración WireGuard con el comando de la salida `ConfiguracionAdministrador` (cambiando el número) y la importa en la app WireGuard.
- Mientras esté conectado, todo su tráfico sale por esa IP. El backend solo atiende `/api/v1/admin/**` desde ella y con rol ADMIN.

## 3. Render

1. *New → Blueprint* con este repositorio. `render.yaml` crea `campusnexo-backend` y `campusnexo-ai`. Si los repositorios cambian de dueño, actualiza la línea `repo:` de la IA.
2. Variables `sync: false` del backend:

   | Variable | Valor |
   |---|---|
   | `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` | Neon |
   | `MONGODB_URI` | Atlas |
   | `APP_USERS` | Al menos un ADMIN: `nombre:ADMIN:<hash>` generado con `python tools/auth/hash_password.py` |
   | `APP_SENSOR_KEYS` | Claves de 24+ caracteres para cámaras, Arduinos, agentes de PC y kioscos (una por dispositivo), separadas por comas |
   | `ADMIN_ALLOWED_NETWORKS` | Salida de la VPN. Obligatoria: en producción el backend no arranca sin ella |
   | `AI_SERVICE_URL` | URL pública de `campusnexo-ai` |
   | `CORS_ALLOWED_ORIGINS` | `https://campusnexo*.vercel.app` y el dominio final |

3. Para la piloto se pueden acortar los temporizadores: `NO_SHOW_GRACE=2m`, `DESK_ABANDON_THRESHOLD=2m`, `EQUIPMENT_IDLE_SHUTDOWN=1m`, `EMPAREJAMIENTO_ANTICIPACION=20s` y `EMPAREJAMIENTO_RANURA=3s`. Si el ajuste del repo de IA lo calculó, también `KALMAN_CAMERA_VARIANCE`.

## 4. Vercel

Importa `campusnexo-frontend`. Variables de build:
- `VITE_API_URL=https://campusnexo-backend.onrender.com/api/v1`
- `VITE_WS_URL=wss://campusnexo-backend.onrender.com/ws`

El navegador llama directo al backend: Vercel no hace proxy de WebSocket, y así el backend ve la IP real del administrador.

## 5. Verificación

```sh
B=https://campusnexo-backend.onrender.com
curl -s $B/actuator/health                                              # {"status":"UP"}
curl -s -o /dev/null -w '%{http_code}\n' $B/api/v1/spaces               # 401: exige sesión
CAMPUSNEXO_ADMIN_USER=… CAMPUSNEXO_ADMIN_PASSWORD=… CAMPUSNEXO_SENSOR_KEY=… python tools/e2e/e2e_admin.py --backend $B --exige-red-admin
```

`e2e_admin.py` debe correrse conectado a la VPN; fuera de ella, el paso 7 confirma que la administración responde 403.
