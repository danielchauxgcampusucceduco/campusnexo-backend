# API CampusNexo v1

Base local: `http://localhost:8080`. JSON en `camelCase`; instantes en ISO-8601 UTC (`2026-10-20T15:00:00Z`). Todas las respuestas de error usan `ProblemDetail` con un `code` estable.

🔑 = requiere la cabecera `X-API-Key` cuando `INGEST_API_KEY` está definida (siempre en producción).

## Catálogo

| Ruta | Descripción |
|---|---|
| `GET /api/v1/spaces?type=BIBLIOTECA` | Lista los espacios (filtro opcional por `AULA`, `BIBLIOTECA`, `LABORATORIO`, `AUDITORIO`, `ZONA_COMUN`). Incluye edificio, sede, perfil climático, temperatura exterior promedio y tarifa de energía (COP/kWh). |
| `GET /api/v1/spaces/{spaceId}` | Un espacio. `404 space_not_found` si no existe. |

Espacios de demostración: `AULA-301`, `LAB-IA-102`, `BIB-PISO-2` (andina), `AULA-MAGNA-204` (tropical) y `AULA-ADM-101` (templada).

## Telemetría

### `POST /api/v1/telemetry` 🔑

```json
{
  "spaceId": "AULA-301",
  "recordedAt": "2026-10-20T15:00:00Z",
  "peopleCount": 18,
  "objectCount": 0,
  "wifiDevices": 21,
  "co2Ppm": 820,
  "temperatureC": 23.1,
  "humidityPct": 58,
  "illuminanceLux": 410,
  "activePowerKw": 2.4
}
```

| Campo | Rango (validado en el dominio) |
|---|---|
| `recordedAt` | Opcional (se usa la hora del servidor). Máximo 5 min en el futuro y 30 días en el pasado. |
| `peopleCount` | 0–5000. Conteo agregado del sensor o la cámara. |
| `objectCount` | 0–10000. Objetos sobre los puestos (lo usa la IA para detectar puestos "apartados"). |
| `wifiDevices` | 0–10000. |
| `co2Ppm` | 0–10000. |
| `temperatureC` | −30 a 60. |
| `humidityPct` | 0–100. |
| `illuminanceLux` | 0–20000. Aporte de luz natural. |
| `activePowerKw` | 0–10000. |

- `201 Created`: lectura nueva, se notifica a los observadores.
- `200 OK`: ya existía una lectura con el mismo `spaceId` y `recordedAt` (reintento idempotente; no se duplica).

### Consultas

- `GET /api/v1/telemetry?spaceId=&limit=20`: más recientes primero; `limit` va de 1 a 100.
- `GET /api/v1/spaces/{spaceId}/telemetry?limit=20`

## Evaluación de punta a punta (Facade)

### `POST /api/v1/spaces/{spaceId}/evaluations`

Sin cuerpo. Usa la última lectura del espacio: estima la ocupación (IA, o respaldo local), deriva el estado del HVAC desde el historial, guarda la recomendación y responde:

```json
{
  "space": { "spaceId": "AULA-301", "climateProfile": "SEDE_ANDINA", "...": "..." },
  "telemetry": { "recordedAt": "...", "peopleCount": 22, "...": "..." },
  "occupancy": { "estimateId": "...", "occupancyEstimate": 22, "confidencePct": 82.0, "source": "SERVICIO_IA" },
  "recommendation": { "hvac": { "mode": "CALEFACCION", "setpointC": 20.0 }, "occupancySource": "SERVICIO_IA", "sourceTelemetryAt": "..." },
  "activeReservation": null,
  "recentAlerts": []
}
```

`409 telemetry_not_available` si el espacio no tiene lecturas.

## Ocupación

| Ruta | Descripción |
|---|---|
| `POST /api/v1/spaces/{spaceId}/occupancy-estimates` | Cuerpo `{ "yoloPeople": 18, "co2Ppm": 820, "wifiDevices": 21, "outdoorCo2Ppm": 420 }` (`yoloPeople` y `outdoorCo2Ppm` son opcionales). `201` con `Location`. |
| `POST /api/v1/occupancy/estimate?spaceId=` | **Ruta anterior**, mantenida para el dashboard React actual (`200`, mismos campos). Está obsoleta. |
| `GET /api/v1/spaces/{spaceId}/occupancy-estimates?limit=` | Historial de estimaciones. |
| `GET /api/v1/occupancy-estimates/{estimateId}` | Una estimación. |
| `GET /api/v1/occupancy/live?type=BIBLIOTECA` | Ocupación en vivo: `peopleCount`, `freeSeats`, `occupancyPct`, `status` (`LIBRE` < 60 % ≤ `MODERADO` < 85 % ≤ `SATURADO`; `SIN_DATOS` si la última lectura tiene más de 30 min). Primero los espacios con más puestos libres. |

Cada estimación indica su `source`: `SERVICIO_IA` o `RESPALDO_LOCAL`. Si la IA está caída y el respaldo está desactivado, la respuesta es `503 ai_service_unavailable`; si la IA responde con datos inválidos, `502 ai_service_invalid_response`.

## Recomendaciones climáticas

### `POST /api/v1/spaces/{spaceId}/climate-recommendations`

```json
{
  "occupancy": 18,
  "co2Ppm": 820,
  "indoorTemperatureC": 17.5,
  "outsideTemperatureC": 12,
  "humidityPct": 60,
  "illuminanceLux": 250,
  "currentHvacMode": "APAGADO",
  "minutesInCurrentHvacMode": 30
}
```

- `outsideTemperatureC` es opcional (se usa el promedio de la sede).
- `currentHvacMode` y `minutesInCurrentHvacMode` se envían juntos o se omiten juntos; si se omiten, se derivan del historial.
- Responde `201` con `Location: /api/v1/climate/recommendations/{id}`.

La respuesta contiene:

- `hvac.mode` (`APAGADO`, `CALEFACCION`, `ENFRIAMIENTO` o `DESHUMIDIFICACION`) y `setpointC` con decimales.
- `ventilation.mode` (`NINGUNA`, `NATURAL` o `MECANICA`).
- `lighting.targetLevelPct` y `lighting.closeBlinds`.
- `explanations`, `occupancySource`, `sourceTelemetryAt` e `inputs` (las entradas usadas, para auditoría).

Consultas:

- `GET /api/v1/climate/recommendations?spaceId=&limit=`
- `GET /api/v1/spaces/{spaceId}/climate-recommendations?limit=`
- `GET /api/v1/climate/recommendations/{recommendationId}`

`POST /api/v1/climate/recommendations?spaceId=` sigue disponible como ruta anterior (obsoleta).

## Alertas

`GET /api/v1/alerts?spaceId=&type=&limit=`: tipos `CO2_ALTO`, `SOBRECUPO`, `CONSUMO_ESPACIO_VACIO` y `RESERVA_SIN_USO`; severidad `ADVERTENCIA` o `CRITICA`. La misma alerta no se repite para un espacio durante `ALERT_COOLDOWN` (15 min). `RESERVA_SIN_USO` se emite una vez por reserva, cuando el espacio sigue vacío `RESERVATION_GRACE` (15 min) después de iniciar.

## Reservas

### `POST /api/v1/spaces/{spaceId}/reservations` 🔑

```json
{ "startsAt": "2026-10-21T13:00:00Z", "endsAt": "2026-10-21T15:00:00Z", "expectedAttendees": 25, "reference": "IS-701" }
```

`reference` es un código de curso o grupo, sin datos personales. Las reglas son: duración máxima de 12 h, `expectedAttendees` ≤ aforo y sin solapamiento (`409 reservation_conflict`). Responde `201` con `Location: /api/v1/reservations/{id}`.

Consultas:

- `GET /api/v1/spaces/{spaceId}/reservations?from=&to=`: por defecto, desde un día atrás hasta siete días adelante; ventana máxima de 31 días.
- `GET /api/v1/reservations/{reservationId}`

## Dashboard

`GET /api/v1/dashboard/summary`

```json
{
  "monitoredSpaces": 3,
  "readingsLast24h": 240,
  "since": "2026-10-19T15:00:00Z",
  "alertsLast24h": 6,
  "alertsByType": { "CO2_ALTO": 2, "SOBRECUPO": 1, "CONSUMO_ESPACIO_VACIO": 3, "RESERVA_SIN_USO": 0 },
  "averageOccupancyPct": 41.5,
  "saturatedSpaces": 1,
  "estimatedEmptySpaceEnergyKwh": 3.25,
  "estimatedEmptySpaceEnergyCostCop": 2811
}
```

La energía en espacios vacíos se **estima** integrando la potencia de las lecturas con `peopleCount = 0` hasta la lectura siguiente, con un tope de 15 min por intervalo. Es un indicador del prototipo, no una medición certificada.

## Errores

| HTTP | `code` | Significado |
|---|---|---|
| 400 | `validation_failed` | Faltan campos (incluye `errors[]` con campo y mensaje). |
| 400 | `invalid_request` | Valor fuera de rango o regla del dominio violada (mensaje con el campo). |
| 400 | `malformed_request` | JSON inválido, tipo incorrecto o parámetro requerido ausente. |
| 401 | `api_key_required` | Falta `X-API-Key` o no es válida. |
| 404 | `space_not_found`, `recommendation_not_found`, `occupancy_estimate_not_found`, `reservation_not_found`, `route_not_found` | No existe. |
| 405 / 415 | `method_not_allowed` / `unsupported_media_type` | Método o tipo de contenido no soportado. |
| 409 | `telemetry_not_available`, `reservation_conflict` | Conflicto con el estado actual. |
| 500 | `internal_error` | Error inesperado (el detalle solo queda en los logs). |
| 502 | `ai_service_invalid_response` | La IA respondió datos inválidos. |
| 503 | `ai_service_unavailable` | La IA no respondió y el respaldo está desactivado. |

```json
{ "type": "about:blank", "title": "Recurso no encontrado", "status": 404, "detail": "No existe el espacio: AULA-999", "code": "space_not_found" }
```

## Cambios respecto a la versión anterior

- Rutas por recurso (`/spaces/{id}/...`); las rutas antiguas de ocupación y recomendaciones siguen funcionando.
- La recomendación manual exige `humidityPct` y reemplaza `hvacOn` por `currentHvacMode` + `minutesInCurrentHvacMode` (ambos opcionales).
- `hvac.setpointC` ahora es decimal y `hvac` y `ventilation` tienen `mode`.
- `POST /telemetry` responde `200` (en lugar de `201`) cuando la lectura ya existía.
