// Lo que muestra el panel, leído del JSON de iot-service (igual por HTTP y por MQTT).
#pragma once

#include <Arduino.h>
#include <ArduinoJson.h>

#include <vector>

// Un parqueo en pantalla
struct ParkingView {
  uint32_t id = 0;
  String name;
  String address;
  int total = 0;       // disponibles + ocupados (mantenimiento no cuenta)
  int available = 0;
  int occupied = 0;
  String state;        // OPEN, FULL, CLOSED, OUT_OF_ORDER
  String notice;       // "CERRADO", "FUERA DE SERVICIO"; vacío si atiende
};

struct PanelData {
  bool valid = false;
  bool avenue = false;           // true = rota varios parqueos
  uint32_t parkingId = 0;        // panel de parqueo: cuál
  int rotationSeconds = 10;
  std::vector<ParkingView> parkings;
  uint32_t receivedAt = 0;       // millis() de la última actualización
};

// Lee un panel de parqueo ({parking_id, parking_name, ...}) o de avenida ({parkings: [...]})
bool parsePanelData(JsonDocument& doc, PanelData& out, int defaultRotation);

// Texto apto para la fuente del panel: sin tildes ni ñ (no existen en la fuente) y en mayúsculas
String panelText(const String& utf8);
