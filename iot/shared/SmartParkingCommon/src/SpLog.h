// Mensajes por el monitor serie con una etiqueta y el tiempo desde el encendido.
// Ejemplo: SP_LOG("WIFI", "Conectado, IP %s", ip) -> [   12.345] WIFI  Conectado, IP 192.168.1.20
#pragma once

#include <Arduino.h>

#define SP_LOG(tag, fmt, ...) \
  Serial.printf("[%9.3f] %-5s " fmt "\n", millis() / 1000.0, tag, ##__VA_ARGS__)
