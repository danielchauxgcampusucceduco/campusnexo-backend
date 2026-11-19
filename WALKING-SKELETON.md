# Walking skeleton · CampusNexo

La implementación más delgada que atraviesa el sistema de punta a punta. No busca ser útil para un usuario final: demuestra la estructura.

| Lo que exige el enunciado | Cómo lo demuestra CampusNexo |
|---|---|
| Servicios en procesos separados que se comunican de verdad | Backend Java, servicio de IA en Python, PostgreSQL, MongoDB y Mosquitto en contenedores distintos, por HTTP, MQTT y STOMP |
| Capas separadas, dependencias en una sola dirección y sin atajos | `HexagonalArchitectureTest` (ArchUnit) falla el build si el dominio importa infraestructura o si hay ciclos; cada evento deja una traza que recorre las seis capas |
| Un evento publicado y al menos dos consumidores separados | Un frame de la cámara publica la lectura a tres observadores (aforo, no-show y registro) y el cambio del gemelo a dos seguidores: el tablero por STOMP y el historial en MongoDB |

## Requisitos

- Docker Desktop.
- Los tres repositorios en carpetas hermanas: `campusnexo-backend`, `campusnexo-ai-service` y `campusnexo-frontend`.
- Node 20 o superior para el tablero (opcional para el paso 3).

## 1. Levantar (un comando)

```bash
cd campusnexo-backend
cp .env.example .env     # define contraseñas, APP_USERS (entre comillas simples), APP_SENSOR_KEYS y APP_TOKEN_SECRET
docker compose --profile sistema up --build -d
```

Comprobar que todo está arriba:

```bash
docker compose ps                                   # database, history, mqtt, backend y ai en marcha
curl -s http://localhost:8080/actuator/health       # {"status":"UP"}
curl -s http://localhost:8001/health                # {"status":"ok", ...}
```

En otra terminal, el tablero:

```bash
cd campusnexo-frontend && npm ci && npm run dev     # http://localhost:5173
```

## 2. Enviar un frame (como la cámara de borde)

Usa una de las claves de `APP_SENSOR_KEYS` del `.env`:

```bash
curl -s -X POST http://localhost:8080/api/v1/twin/telemetry \
  -H "Content-Type: application/json" -H "X-API-Key: <clave-de-sensor>" \
  -d '{"space_id":"HEMEROTECA-3A-ESTUDIO","camera_online":true,"emulated":true,
       "detections":[{"track_id":1,"class":"person","confidence":0.9,"bbox":[0.20,0.30,0.26,0.42]},
                     {"track_id":2,"class":"person","confidence":0.9,"bbox":[0.60,0.30,0.66,0.42]},
                     {"track_id":3,"class":"person","confidence":0.9,"bbox":[0.40,0.70,0.46,0.82]}]}'
```

El frame es solo de cámara: detecciones, sin sensores de ambiente. La respuesta trae el gemelo de la sala de estudio privado de la hemeroteca recalculado: ocupación, docentes y estudiantes presentes, y escenarios reconocidos.

## 3. Ver los consumidores del evento

- **Tablero (STOMP):** la sala cambia en el plano del piso 3 de `http://localhost:5173` sin recargar.
- **Alertas (Observer):** 3 personas en una sala de aforo 2 generan la alerta de **sobrecupo** en la vista Alertas.
- **Disponibilidad:** la sala deja de figurar en «Libres ahora» de la vista Disponibilidad.
- **Historial (MongoDB):**

  ```bash
  docker compose exec history mongosh campusnexo --quiet --eval 'db.historial_gemelo.countDocuments()'
  ```

  El número sube con cada frame que cambia el gemelo.

## 4. Ver las capas

- **La traza del evento:** vista «Cómo funciona» del tablero, o `GET /api/v1/twin/traces?limit=1` con una sesión iniciada. Recorre `INBOUND_ADAPTER → INPUT_PORT → USE_CASE → DOMAIN_SERVICE → OUTPUT_PORT → OUTBOUND_ADAPTER`.
- **Las reglas de dependencia:**

  ```bash
  docker run --rm -v "$PWD":/w -w /w maven:3.9-eclipse-temurin-21 mvn -q test -Dtest=HexagonalArchitectureTest
  ```

  Para demostrar que la regla funciona, se puede importar una clase de Spring en una clase del dominio y volver a correr: el build falla.

## 5. Romper algo

```bash
docker compose stop ai
```

Repetir el paso 2: la ingesta sigue respondiendo 200 con el respaldo local (circuito abierto). Las herramientas de IA del tablero muestran «El servicio de IA no respondió» y el resto sigue funcionando. Para volver: `docker compose start ai`.

## Apagar

```bash
docker compose --profile sistema down       # agrega -v para borrar también los datos
```
