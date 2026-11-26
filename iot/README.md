# IoT - Firmware de los dispositivos

Código de los microcontroladores del prototipo. **Todavía no hay firmware**: las carpetas están
vacías. El servicio que los atiende ya está hecho
([`backend/services/iot-service`](../backend/services/iot-service/README.md)).

> `iot/` = firmware que corre en los ESP32. `backend/services/iot-service` = servicio Go que
> recibe sus datos y orquesta ia-service y parking-service.

## Dispositivos

| Carpeta | Dispositivo | Protocolo | Qué hace |
|---|---|---|---|
| `esp32-cam-frontal/` | ESP32-CAM en la entrada y la salida (escenario A) | HTTP | Toma la foto al detectar un vehículo, la envía y abre la barrera si la respuesta trae `open_gate: true` |
| `esp32-cam-aerial/` | ESP32-CAM aérea (escenario B) | HTTP | Foto periódica del parqueo abierto para la ocupación por visión |
| `esp32-display-led/` | ESP32 + panel LED | MQTT (o HTTP) | Muestra disponibles y ocupados de un parqueo, o rota todos los parqueos en una avenida |
| (por crear) | ESP32 + HC-SR04 | MQTT | Mide la distancia y publica la lectura de un espacio |
| (por crear) | Kiosco ESP32-S3 + pantalla táctil | HTTP | Pago de la deuda de calle con QR |
| `shared/` | — | — | Código común (WiFi, configuración, reconexión) |

## Contrato con iot-service

Todas las peticiones HTTP van al **api-gateway** (puerto 8080).

### Cámara de entrada o salida

```http
POST /api/iot-service/v1/captures
Content-Type: application/json
X-Device-ID: CAM-PSM-ENTRADA

{ "device_id": "CAM-PSM-ENTRADA", "capture_type": "ENTRY", "image_base64": "<JPG en base64>" }
```

`capture_type` es `ENTRY` o `EXIT`; la imagen, máximo 10 MB. La zona y el parqueo salen del
registro del dispositivo (`parking_id` es opcional y, si se envía, debe coincidir). La respuesta llega en hasta 15 s con
`open_gate`, `outcome` y `message`; si tarda más, responde 202 y la cámara consulta
`GET /api/iot-service/v1/captures/{captureId}/status`. Límite: 10 capturas por minuto.

### Sensor HC-SR04

- Broker MQTT (puerto 1883; TLS 8883 en la nube), usuario y clave propios del sensor.
- Publica en `smartparking/sensors/{deviceId}/reading`: `{"distance_cm": 42}`.
- Hasta 80 cm se considera ocupado. iot-service aplica el antirrebote: el sensor puede
  publicar cada pocos segundos.

### Panel LED

| Tipo | Tema MQTT (retenido) | Mostrar |
|---|---|---|
| Junto al parqueo | `smartparking/parkings/{parkingId}/availability` | Nombre, total, disponibles, ocupados |
| De avenida | `smartparking/panels/{deviceId}/availability` | Rotar la lista `parkings[]` cada `rotation_seconds` (10 por defecto) con nombre, dirección y números |

Si `state` no es `OPEN`, se muestra `notice` ("LLENO", "CERRADO", "FUERA DE SERVICIO") en lugar
de los números. Alternativa HTTP: `POST /api/iot-service/v1/displays/availability` con la
cabecera `X-Device-ID` (una consulta cada 10 s).

## Registro de un dispositivo

1. Crear el dispositivo en la tabla `devices` de iot-service (tipo, parqueo y zona).
2. Si usa MQTT, añadir `deviceId:clave` a `backend/docker-init/mosquitto/devices.txt` (no se
   versiona) y reiniciar Mosquitto.
3. Grabar en el firmware la URL del gateway o del broker, el `deviceId` y su clave (nunca en el
   repositorio).

Pendiente: credencial por dispositivo (API key) para las peticiones HTTP.
