# iot-service

Servicio en **Go 1.26** (Gin, GORM, Paho MQTT), puerto **8083**. Es el **orquestador del IoT**:
recibe las fotos de las ESP32-CAM, las lecturas de los sensores HC-SR04 y las consultas de los
paneles LED, y habla con ia-service y parking-service.

Arquitectura **por capas** (no hexagonal), pensada para muchas peticiones pequeñas.
Base de datos MySQL 8.0 (`iot_service_db`) + Redis (caché y límite de peticiones).

---

## Qué hace

### 1. Cámaras de entrada y salida (escenario A, HTTP)

```text
ESP32-CAM ─POST /captures─► iot-service ─► MinIO/GCS (foto)
                               │ multipart
                               ├──────────► ia-service  (placa + confianza)
                               │ token de servicio
                               └──────────► parking-service POST /internal/captures
                                              decide: registrar, "Por revisar" o cobrar
                    ◄── open_gate, outcome, message ──┘
```

- La cámara **espera la respuesta** hasta `CAPTURE_TIMEOUT_SECONDS` (15). Si se pasa, recibe 202
  y consulta `GET /captures/:captureId/status`.
- La **zona** de cada cámara está en el dispositivo (`devices.zone_id`); solo se aceptan
  dispositivos `ESP32_CAM_FRONTAL` con parqueo asignado.
- Si ia-service falla, la captura se envía sin placa y queda en **Por revisar** del panel.
- iot-service no aplica el umbral de confianza: lo decide parking-service (0.85).

### 2. Sensores HC-SR04 (MQTT)

- Tema: `smartparking/sensors/{deviceId}/reading` con `{"distance_cm": 42}`.
- Distancia válida 3-400 cm; **≤ 80 cm = vehículo** (`SENSOR_OCCUPIED_MAX_CM`).
- Antirrebote de 5 s (`SENSOR_DEBOUNCE_SECONDS`): solo se envía un cambio estable.
- Se guarda en `sensor_readings` (solo los cambios) y se avisa a parking-service
  (`POST /internal/sensors/readings`), que mueve la estadía al espacio real o lo libera.

### 3. Paneles LED (MQTT retenido o HTTP)

| Panel | Dispositivo | Tema MQTT | Muestra |
|---|---|---|---|
| Junto al parqueo | `ESP32_DISPLAY` con `parking_id` | `smartparking/parkings/{id}/availability` | Nombre, total, disponibles, ocupados |
| De avenida | `ESP32_DISPLAY` sin `parking_id` | `smartparking/panels/{deviceId}/availability` | Todos los parqueos, uno cada `rotation_seconds` |

- Se publica **solo cuando cambia un número**, al instante tras un sensor, y se republica al
  reconectar. Los mensajes quedan retenidos: un panel que se enciende recibe el último dato.
- Panel de avenida: en `devices.metadata`, `{"parking_ids":[2,1], "rotation_seconds":10}`
  (ambos opcionales; sin lista, todos los parqueos; rotación de 5 a 60 s). La rotación la hace el
  firmware con la lista recibida.
- Estados: `OPEN`, `FULL`, `CLOSED`, `OUT_OF_ORDER`, con un `notice` ("CERRADO", "FUERA DE
  SERVICIO") que se muestra en lugar de los números.
- Alternativa HTTP: `POST /api/v1/displays/availability`. El firmware debe enviar la cabecera
  **`X-Device-ID`**: el límite (1 consulta cada 10 s) es por dispositivo; sin ella, por IP.

La disponibilidad viene de los endpoints públicos de parking-service y se guarda en Redis
`DISPLAY_CACHE_SECONDS` (5 s).

---

## Endpoints

| Método y ruta | Descripción |
|---|---|
| `GET /health`, `/ready`, `/live`, `/metrics` | Salud |
| `POST /api/v1/captures` | Foto de una ESP32-CAM (10 por minuto por dispositivo; imagen ≤ 10 MB) |
| `GET /api/v1/captures/:captureId/status` | Estado de una captura |
| `GET /api/v1/captures/device/:deviceId` | Capturas de un dispositivo |
| `POST /api/v1/displays/availability` | Disponibilidad para un panel (1 cada 10 s) |
| `GET /api/v1/displays/analytics/:deviceId`, `/analytics/parking/:parkingId` | Analítica (no publicada en el gateway) |
| `DELETE /api/v1/displays/cache/:parkingId` | Borrar la caché (no publicada en el gateway) |

Límite global: 100 peticiones por minuto por IP (Redis). Por el gateway solo se publican
`/api/iot-service/v1/captures/**` y `/api/iot-service/v1/displays/availability`.

---

## Fotos

Interfaz `ImageStorage` con dos implementaciones (`STORAGE_PROVIDER`):

| Valor | Uso |
|---|---|
| `minio` | Desarrollo local (contenedor `smart-parking-minio`, API compatible con S3) |
| `gcs` | Google Cloud Storage: bucket privado y URL firmada V4 (`GCS_BUCKET_NAME`, credenciales de la VM o `GOOGLE_APPLICATION_CREDENTIALS`) |

`STORAGE_SIGNED_URL_HOURS` (máximo 168): pasado ese tiempo la foto de una revisión antigua deja
de verse.

---

## Configuración

Copia `.env.example` a `.env` (no se versiona). Variables principales:

| Variable | Por defecto | Para |
|---|---|---|
| `SERVER_PORT` | 8083 | Puerto HTTP |
| `MYSQL_*`, `REDIS_*` | localhost | Conexiones |
| `IA_SERVICE_URL` | `http://localhost:8084` | ia-service (directo, sin gateway) |
| `PARKING_SERVICE_URL` | `http://localhost:8082` | parking-service (directo, sin gateway) |
| `AUTH_SERVICE_URL` | `http://localhost:8081` | Pedir el token de servicio |
| `IOT_SERVICE_CLIENT_ID` / `IOT_SERVICE_CLIENT_SECRET` | `iot-service` / — | Credenciales del token de servicio (el mismo secreto de `backend/.env`) |
| `CAPTURE_TIMEOUT_SECONDS` | 15 | Espera máxima de la cámara |
| `MQTT_ENABLED`, `MQTT_BROKER_URL` | `true`, `tcp://localhost:1883` | Broker Mosquitto |
| `MQTT_IOT_USERNAME` / `MQTT_IOT_PASSWORD` | `iot-service` / — | Usuario MQTT (el mismo de `backend/.env`) |
| `SENSOR_OCCUPIED_MAX_CM`, `SENSOR_DEBOUNCE_SECONDS` | 80, 5 | Sensores |
| `DISPLAY_CACHE_SECONDS`, `AVAILABILITY_PUBLISH_SECONDS` | 5, 5 | Paneles |
| `STORAGE_PROVIDER`, `S3_*`, `GCS_BUCKET_NAME` | `minio` | Fotos |

---

## Ejecutar

```bash
# Esquema de la base (primera vez)
docker exec -i db-system-smart-parking-mysql mysql -uroot -p iot_service_db < scripts/init.sql
# Sobre una base ya creada, aplica en orden scripts/migrations/*.sql

go run ./cmd
curl -s localhost:8083/health
```

## Pruebas

```bash
go build ./... && go vet ./... && go test ./...
```

Las pruebas usan dobles en memoria: no necesitan MySQL, Redis, MQTT ni los otros servicios.

---

## Estructura

```text
cmd/main.go                      arranque y cableado
internal/
  api/router.go                  rutas
  api/handlers/                  capture, display, health
  api/middleware/                cors, logger, rate_limit
  config/config.go               variables de entorno
  database/                      mysql, redis
  messaging/mqtt.go              entrada y salida MQTT
  models/                        camera_capture, device, display_log, parking_snapshot,
                                 sensor_reading, space_detection
  repository/                    acceso a MySQL por modelo
  services/                      ia_client, parking_client, service_token, sensor_service,
                                 availability_service, availability_publisher,
                                 image_storage (minio, gcs)
pkg/httpclient/                  cliente HTTP con reintentos
scripts/init.sql, migrations/    esquema de MySQL
```

## Pendiente

- Visión aérea (escenario B): la llamada de ocupación masiva a parking-service no tiene endpoint
  todavía.
- Heartbeat de dispositivos (`LastSeenAt`) y snapshots: rutas comentadas en el router.
- TLS para MQTT (8883) en la nube.
