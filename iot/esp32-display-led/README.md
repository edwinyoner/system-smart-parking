# Panel LED de disponibilidad

**Placa:** ESP32-S3-DevKitC-1 N16R8 + **2 paneles HUB75 P5 de 64x32** en cadena (128x32).

## Qué muestra

**Panel junto al parqueo** (registrado con `parking_id`), de arriba hacia abajo:

```text
PARQUEO SAN MARTIN
TOTAL              30
DISPONIBLES        12     (verde; "LLENO" en rojo si es 0)
OCUPADOS           18     (rojo)
```

**Panel de avenida** (sin `parking_id`): rota los parqueos, uno cada `rotation_seconds` (10 por
defecto), con nombre, dirección, `DISP` y `OCUP`, total y la página (`1/3`).

Si el parqueo no atiende, en lugar de los números va el aviso: `CERRADO` o `FUERA DE SERVICIO`.
Sin datos por 2 minutos: `SIN CONEXION` (nunca números viejos). Los textos largos se desplazan.
Las tildes y la ñ se quitan, porque la fuente del panel no las tiene.

## Cómo recibe los datos

1. Al encender: `POST /api/iot-service/v1/displays/availability` con su `device_id`. La respuesta
   dice si es panel de parqueo o de avenida, y trae los datos.
2. Se suscribe por MQTT al tema que le toca (mensaje **retenido**: al conectarse llega el último
   dato) y recibe cada cambio al instante:
   - parqueo: `smartparking/parkings/{parkingId}/availability`
   - avenida: `smartparking/panels/{deviceId}/availability`
3. Si MQTT se cae o no trae datos, vuelve a preguntar por HTTP cada 10 s. Cada 10 minutos lo
   comprueba por HTTP, por si cambió la configuración del panel.

## Conexiones (conector HUB75 de entrada del primer panel)

| HUB75 | ESP32-S3 | HUB75 | ESP32-S3 |
|---|---|---|---|
| R1 | GPIO 4 | A | GPIO 17 |
| G1 | GPIO 5 | B | GPIO 18 |
| B1 | GPIO 6 | C | GPIO 8 |
| R2 | GPIO 7 | D | GPIO 9 |
| G2 | GPIO 15 | LAT | GPIO 10 |
| B2 | GPIO 16 | OE | GPIO 11 |
| GND | GND | CLK | GPIO 12 |

- La salida del primer panel va a la entrada del segundo (cable plano que traen).
- **Los paneles se alimentan aparte**: fuente de **5 V y 4 A** a sus bornes. El ESP32 no puede
  alimentarlos. GND común entre la fuente y la placa.
- Se evitan los pines de arranque, USB, flash y PSRAM de la N16R8. Pines en `include/config.h`.

## Configurar y grabar

```bash
cd iot/esp32-display-led
cp include/secrets.example.h include/secrets.h   # WiFi, gateway, broker, deviceId y clave MQTT
pio run -t upload && pio device monitor
```

`PANEL_BRIGHTNESS` (0-255) en `config.h`: bajarlo de noche o en interiores.

## Archivos

| Archivo | Qué hace |
|---|---|
| `src/main.cpp` | Conexión, HTTP al encender, MQTT, respaldo HTTP y rotación |
| `src/panel_data.*` | Lee el JSON (igual por HTTP y MQTT) y limpia el texto para la fuente |
| `src/display.*` | Dibuja con doble búfer; redibuja solo si cambia algo o al desplazar un texto |
