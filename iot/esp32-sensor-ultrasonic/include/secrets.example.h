// Copia este archivo como secrets.h (no se sube a GitHub) y pon los datos reales.
#pragma once

#define SP_WIFI_SSID "nombre-de-la-red"
#define SP_WIFI_PASSWORD "clave-de-la-red"

// Broker Mosquitto: 1883 en la red local; en la nube 8883 con TLS (y SP_MQTT_ROOT_CA)
#define SP_MQTT_HOST "192.168.1.50"
#define SP_MQTT_PORT 1883

// deviceId registrado en iot-service (tipo de sensor, con su espacio) y su clave MQTT,
// la misma línea "deviceId:clave" de backend/docker-init/mosquitto/devices.txt
#define SP_DEVICE_ID "SENSOR-PSM-A01"
#define SP_MQTT_PASSWORD "clave-del-sensor"

// #define SP_MQTT_ROOT_CA "-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----\n"
