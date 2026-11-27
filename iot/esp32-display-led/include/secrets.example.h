// Copia este archivo como secrets.h (no se sube a GitHub) y pon los datos reales.
#pragma once

#define SP_WIFI_SSID "nombre-de-la-red"
#define SP_WIFI_PASSWORD "clave-de-la-red"

// api-gateway: al encender, el panel pregunta qué mostrar (y si es de parqueo o de avenida)
#define SP_API_BASE_URL "http://192.168.1.50:8080/api/iot-service/v1"

// Broker Mosquitto: 1883 en la red local; en la nube 8883 con TLS (y SP_ROOT_CA)
#define SP_MQTT_HOST "192.168.1.50"
#define SP_MQTT_PORT 1883

// deviceId registrado en iot-service como ESP32_DISPLAY (con parking_id = panel del parqueo;
// sin parking_id = panel de avenida) y su clave MQTT (devices.txt de Mosquitto)
#define SP_DEVICE_ID "DISPLAY-PSM-01"
#define SP_MQTT_PASSWORD "clave-del-panel"

// #define SP_ROOT_CA "-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----\n"
