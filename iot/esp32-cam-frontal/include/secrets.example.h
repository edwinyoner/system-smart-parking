// Copia este archivo como secrets.h (no se sube a GitHub) y pon los datos reales.
#pragma once

// Red WiFi del parqueo
#define SP_WIFI_SSID "nombre-de-la-red"
#define SP_WIFI_PASSWORD "clave-de-la-red"

// api-gateway (única puerta pública). En la red local: IP de la PC que corre el backend
#define SP_API_BASE_URL "http://192.168.1.50:8080/api/iot-service/v1"

// Debe existir en la tabla devices de iot-service: tipo ESP32_CAM_FRONTAL, con parqueo y zona
#define SP_DEVICE_ID "CAM-PSM-ENTRADA"

// ENTRY en la entrada, EXIT en la salida
#define SP_CAPTURE_TYPE "ENTRY"

// Solo con https: certificado raíz del servidor (PEM). Sin definir = http
// #define SP_ROOT_CA "-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----\n"
