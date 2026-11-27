// Conexión WiFi con reintentos. El ESP32 se reconecta solo si se cae la red; ensure() lo
// comprueba en cada vuelta de loop() y reinicia la placa si lleva demasiado tiempo sin red.
#pragma once

#include <Arduino.h>

namespace sp {

class SpWifi {
 public:
  // hostname: nombre del dispositivo en la red (se usa el deviceId)
  void begin(const char* ssid, const char* password, const char* hostname);

  // Espera la conexión hasta timeoutMs. true si quedó conectado
  bool waitConnected(uint32_t timeoutMs);

  // Llamar en loop(): registra caídas y reinicia si pasan restartAfterMs sin red
  void ensure(uint32_t restartAfterMs = 120000);

  bool connected() const;

 private:
  uint32_t disconnectedSince_ = 0;
  bool wasConnected_ = false;
};

}  // namespace sp
