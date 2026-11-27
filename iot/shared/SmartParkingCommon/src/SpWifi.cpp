#include "SpWifi.h"

#include <WiFi.h>

#include "SpLog.h"

namespace sp {

void SpWifi::begin(const char* ssid, const char* password, const char* hostname) {
  WiFi.mode(WIFI_STA);
  WiFi.setHostname(hostname);
  // Sin ahorro de energía: la latencia baja importa más (barrera, panel)
  WiFi.setSleep(false);
  WiFi.setAutoReconnect(true);
  WiFi.begin(ssid, password);
  SP_LOG("WIFI", "Conectando a \"%s\"...", ssid);
}

bool SpWifi::waitConnected(uint32_t timeoutMs) {
  uint32_t start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < timeoutMs) {
    delay(250);
  }
  if (connected()) {
    wasConnected_ = true;
    SP_LOG("WIFI", "Conectado. IP %s, senal %d dBm", WiFi.localIP().toString().c_str(), WiFi.RSSI());
    return true;
  }
  SP_LOG("WIFI", "Sin conexion tras %lu ms", (unsigned long)timeoutMs);
  return false;
}

void SpWifi::ensure(uint32_t restartAfterMs) {
  if (connected()) {
    if (!wasConnected_) {
      SP_LOG("WIFI", "Reconectado. IP %s", WiFi.localIP().toString().c_str());
    }
    wasConnected_ = true;
    disconnectedSince_ = 0;
    return;
  }
  if (wasConnected_) {
    SP_LOG("WIFI", "Se perdio la red; reintentando");
    wasConnected_ = false;
  }
  if (disconnectedSince_ == 0) {
    disconnectedSince_ = millis();
  } else if (millis() - disconnectedSince_ > restartAfterMs) {
    // A veces la pila WiFi queda trabada: reiniciar es lo más fiable
    SP_LOG("WIFI", "Demasiado tiempo sin red: reiniciando la placa");
    delay(100);
    ESP.restart();
  }
}

bool SpWifi::connected() const { return WiFi.status() == WL_CONNECTED; }

}  // namespace sp
