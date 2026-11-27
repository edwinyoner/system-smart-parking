# IoT - Firmware de los dispositivos

Firmware de los microcontroladores del prototipo, en **C++ con Arduino-ESP32 3.x sobre
PlatformIO**. Lo atiende [`backend/services/iot-service`](../backend/services/iot-service/README.md).

> `iot/` = firmware que corre en las placas. `backend/services/iot-service` = servicio Go que recibe
> sus datos y orquesta ia-service y parking-service. Las placas **no reconocen placas**: solo
> capturan y envían; la IA corre en ia-service.

## Dispositivos

| Carpeta | Dispositivo | Placa | Protocolo | Estado |
|---|---|---|---|---|
| [`esp32-cam-frontal/`](esp32-cam-frontal/README.md) | Cámara de entrada o salida (escenario A) | Freenove ESP32-S3 WROOM CAM | HTTP | Listo (compila; falta probar con la placa) |
| [`esp32-display-led/`](esp32-display-led/README.md) | Panel LED de disponibilidad | ESP32-S3-DevKitC-1 N16R8 + 2 paneles HUB75 P5 64x32 | MQTT + HTTP | Listo (compila; falta probar con la placa) |
| [`esp32-sensor-ultrasonic/`](esp32-sensor-ultrasonic/README.md) | Sensor de un espacio | ESP32-C6-DevKitC-1 + HC-SR04 | MQTT | Listo (compila; falta probar con la placa) |
| [`esp32-cam-aerial/`](esp32-cam-aerial/README.md) | Cámara aérea (escenario B) | Freenove ESP32-S3 WROOM CAM + OV5640 | HTTP | Pendiente: falta el endpoint en el backend |
| [`esp32-kiosk/`](esp32-kiosk/README.md) | Kiosco de pago en la calle | Waveshare ESP32-S3-Touch-LCD-4.3 | HTTP | Pendiente: falta el endpoint en el backend |
| [`shared/`](shared/SmartParkingCommon) | Librería común | - | - | WiFi con reconexión, HTTP, MQTT, HC-SR04 |

Todas las placas tienen WiFi y Bluetooth (el ESP32-C6, además, WiFi 6).

```text
            HTTP (gateway :8080)                         MQTT (Mosquitto :1883)
ESP32-S3 CAM ──────────────────► iot-service ◄──────────── ESP32-C6 + HC-SR04
 (foto + barrera)                    │   └──────────────────► ESP32-S3 + panel HUB75
                                     ├──► ia-service (placa)
                                     └──► parking-service (entrada, salida, ocupación)
```

## Herramientas

1. Instalar [VS Code](https://code.visualstudio.com/) y la extensión **PlatformIO IDE**.
2. Abrir **la carpeta de un dispositivo** (por ejemplo `iot/esp32-cam-frontal`), no la de `iot/`.
3. La primera compilación descarga los compiladores y librerías (varios minutos, una sola vez).

Desde la terminal (con PlatformIO instalado):

```bash
cd iot/esp32-cam-frontal
pio run                  # compilar
pio run -t upload        # grabar en la placa conectada por USB
pio device monitor       # ver los mensajes de la placa (115200 baudios)
```

Se usa la plataforma [pioarduino](https://github.com/pioarduino/platform-espressif32)
(Arduino-ESP32 3.x), porque la oficial de PlatformIO se quedó en la 2.x y no soporta la ESP32-C6.

## Configuración y claves

Cada dispositivo tiene:

- `include/config.h`: pines y tiempos (se versiona).
- `include/secrets.example.h`: plantilla de los datos privados (se versiona).
- `include/secrets.h`: **copia de la plantilla con los datos reales** (WiFi, URL, deviceId, clave
  MQTT). Está en `.gitignore`: **nunca se sube a GitHub**. Sin este archivo no compila (el error
  lo indica).

```bash
cp include/secrets.example.h include/secrets.h   # y completar
```

## Registrar un dispositivo

1. Crear el dispositivo en la tabla `devices` de iot-service:
   - Cámara: tipo `ESP32_CAM_FRONTAL`, con `parking_id` y `zone_id`.
   - Panel: tipo `ESP32_DISPLAY`; con `parking_id` = panel del parqueo; sin él = panel de avenida
     (en `metadata`: `{"parking_ids": [2, 1], "rotation_seconds": 10}`, ambos opcionales).
   - Sensor: además, en el panel, editar el espacio y poner el `deviceId` como su **sensor**
     (`Space.sensorId`): así parking-service sabe qué espacio mide.
2. Si usa MQTT (sensor y panel), añadir `deviceId:clave` a
   `backend/docker-init/mosquitto/devices.txt` (no se versiona) y reiniciar Mosquitto. El usuario
   MQTT es el `deviceId`: Mosquitto solo le deja escribir o leer sus propios temas.
3. Poner en `secrets.h` el mismo `deviceId` y la clave.

## Contrato con iot-service

Las peticiones HTTP van al **api-gateway** (`/api/iot-service/v1`) con la cabecera `X-Device-ID`.

| Dispositivo | Qué envía | Qué recibe |
|---|---|---|
| Cámara | `POST /captures` `{"device_id","capture_type":"ENTRY"\|"EXIT","image_base64"}` | En hasta 15 s: `open_gate`, `outcome`, `message`, `plate_number`, `confidence`. Si tarda más, 202 y se consulta `GET /captures/{id}/status` |
| Sensor | MQTT `smartparking/sensors/{deviceId}/reading` `{"distance_cm": 42.5}` | - (iot-service decide: hasta 80 cm = ocupado, cambio confirmado si dura 5 s) |
| Panel de parqueo | `POST /displays/availability` `{"device_id"}` al encender | `mode: PARKING` y luego, por MQTT retenido, `smartparking/parkings/{id}/availability` |
| Panel de avenida | Igual | `mode: AVENUE` y por MQTT `smartparking/panels/{deviceId}/availability` (lista `parkings[]`) |

Datos del panel: `parking_name`, `total_spaces`, `available_spaces`, `occupied_spaces`, `state`
(`OPEN`, `FULL`, `CLOSED`, `OUT_OF_ORDER`) y `notice` ("CERRADO", "FUERA DE SERVICIO"), que se
muestra en lugar de los números. Límites: 10 capturas por minuto por cámara; 1 consulta HTTP cada
10 s por panel.

## Pendiente

- Credencial por dispositivo (API key) para las peticiones HTTP: hoy iot-service identifica al
  dispositivo solo por `X-Device-ID`.
- TLS en la nube: HTTPS en el gateway y MQTT en el 8883. El firmware ya lo admite (`SP_ROOT_CA`).
- Probar cada firmware con las placas.
