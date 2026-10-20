# API CampusNexo v1

Base local: `http://localhost:8080`. Los objetos usan JSON en `camelCase`; las solicitudes al microservicio Python usan `snake_case` dentro del Adapter.

## Catálogo de espacios

### `GET /api/v1/spaces`

Lista espacios con edificio, sede, ciudad, perfil climático, capacidad y temperatura exterior promedio de referencia. El catálogo inicial se crea con Flyway.

Identificadores de demostración: `AULA-301`, `AULA-MAGNA-204`, `BIB-PISO-2`, `LAB-IA-102` y `AULA-ADM-101`. Las tres sedes representan perfiles andino, tropical y templado; son datos ilustrativos.

## Telemetría

### `POST /api/v1/telemetry`

Registra una lectura para un espacio existente. Responde `201 Created` con la medición persistida. `recordedAt` es opcional; si se omite, se asigna el instante UTC del backend.

| Campo | Tipo | Restricción |
|---|---|---|
| `spaceId` | string | Obligatorio, máximo 32 caracteres; debe existir en el catálogo. |
| `recordedAt` | ISO-8601 instant | Opcional. |
| `peopleCount`, `objectCount`, `wifiDevices` | integer | Obligatorios, no negativos. |
| `co2Ppm` | number | Obligatorio, entre 0 y 10000. |
| `temperatureC` | number | Obligatorio, entre -30 y 60. |
| `humidityPct` | number | Obligatorio, entre 0 y 100. |
| `illuminanceLux` | number | Obligatorio, entre 0 y 20000. |
| `activePowerKw` | number | Obligatorio, entre 0 y 10000. |

```json
{
  "spaceId": "AULA-301",
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

Después de persistir, se publica un evento que evalúan observadores independientes de CO₂, aforo y consumo con espacio vacío. Las alertas de este prototipo aparecen en los logs.

### `GET /api/v1/telemetry`

Query params opcionales: `spaceId` y `limit` (1–100, por defecto 20). Si se da un `spaceId` desconocido, responde `404`.

### `GET /api/v1/dashboard/summary`

Devuelve `monitoredSpaces` (espacios con al menos una lectura en la ventana), `readingsLast24h` y el instante UTC `since` que inicia el periodo.

## Estimación de ocupación

### `POST /api/v1/occupancy/estimate?spaceId=AULA-301`

El `spaceId` es obligatorio y debe existir. El backend valida el catálogo y adapta la solicitud al servicio IA en `/v1/occupancy/estimate`.

```json
{
  "yoloPeople": 18,
  "co2Ppm": 820,
  "wifiDevices": 21,
  "outdoorCo2Ppm": 415
}
```

`yoloPeople` y `outdoorCo2Ppm` son opcionales. El resto son obligatorios. El timeout inicial de conexión es 2 s y el de lectura 5 s, configurables por entorno. Una respuesta externa no válida o el fallo/timeout del servicio IA se transforma en `503`.

La integración trabaja con conteos agregados. Este backend no recibe, procesa ni guarda imágenes.

## Recomendaciones climáticas

### `POST /api/v1/climate/recommendations?spaceId=AULA-301`

Genera recomendaciones para HVAC, ventilación e iluminación y las persiste con sus explicaciones. `outsideTemperatureC` es opcional; cuando se omite se usa el valor de referencia de la sede.

```json
{
  "occupancy": 18,
  "co2Ppm": 820,
  "indoorTemperatureC": 27.1,
  "illuminanceLux": 380,
  "hvacOn": false,
  "minutesInCurrentHvacState": 12
}
```

La respuesta incluye `recommendationId`, `spaceId`, `climateProfile`, `generatedAt`, las tres decisiones (cada una con explicación) y `explanations`.

### `GET /api/v1/climate/recommendations`

Parámetros opcionales: `spaceId` y `limit` (1–100, por defecto 20). Devuelve las recomendaciones más recientes primero. El filtro de espacio se valida contra el catálogo.

Los perfiles (`SEDE_ANDINA`, `SEDE_TROPICAL`, `SEDE_TEMPLADA`) aplican políticas distintas. Los umbrales y setpoints son de demostración; no representan un cálculo energético certificado ni deben accionar equipos.

## Errores

Las respuestas usan `ProblemDetail` e incluyen un código estable. Por ejemplo:

```json
{
  "type": "about:blank",
  "title": "Espacio no encontrado",
  "status": 404,
  "detail": "No existe el espacio: AULA-999",
  "code": "space_not_found"
}
```

| HTTP | Código | Significado |
|---|---|---|
| `400` | `validation_failed`, `invalid_request`, `malformed_request` | Campos, parámetros o formato inválidos. |
| `404` | `space_not_found` | El espacio no está en el catálogo. |
| `503` | `ai_service_unavailable` | La IA no respondió dentro del timeout o devolvió datos no válidos. |

La API no incluye autenticación ni autorización; no la expongas en una red pública como servicio sin añadir controles de acceso, TLS y configuración de secretos.
