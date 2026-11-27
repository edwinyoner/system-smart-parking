// Configuración del panel LED. Red y credenciales en secrets.h.
#pragma once

// ========================= PANELES HUB75 =========================
// Dos paneles P5 de 64x32 en cadena = 128x32 (4 líneas de texto de 8 px)
#define PANEL_WIDTH 64
#define PANEL_HEIGHT 32
#define PANEL_CHAIN 2

// Pines del conector HUB75 (ESP32-S3-DevKitC-1 N16R8). Se evitan los pines de arranque
// (0, 3, 45, 46), USB (19, 20), flash (26-32) y PSRAM octal (33-37)
#define HUB75_R1 4
#define HUB75_G1 5
#define HUB75_B1 6
#define HUB75_R2 7
#define HUB75_G2 15
#define HUB75_B2 16
#define HUB75_A 17
#define HUB75_B 18
#define HUB75_C 8
#define HUB75_D 9
#define HUB75_E -1   // solo paneles de 64 filas
#define HUB75_LAT 10
#define HUB75_OE 11
#define HUB75_CLK 12

// Brillo 0-255. De noche o en interiores conviene bajarlo (también consume menos)
#define PANEL_BRIGHTNESS 80

// ========================= DATOS =========================
// Respaldo por HTTP si MQTT no trae datos: iot-service admite 1 consulta cada 10 s por panel
#define HTTP_POLL_MS 10000
// Sin datos nuevos por este tiempo: se muestra "SIN CONEXION" en lugar de números viejos
#define STALE_AFTER_MS 120000
// Cada tanto se vuelve a preguntar por HTTP qué mostrar (por si se reconfiguró el panel)
#define RECHECK_MODE_MS 600000

// ========================= ANIMACIÓN =========================
// Texto más largo que el panel: se desplaza 1 px cada SCROLL_STEP_MS
#define SCROLL_STEP_MS 35
// Panel de avenida: segundos por parqueo si el servidor no lo indica
#define DEFAULT_ROTATION_SECONDS 10
