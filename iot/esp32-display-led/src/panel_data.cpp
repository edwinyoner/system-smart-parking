#include "panel_data.h"

namespace {

ParkingView readParking(JsonVariantConst p, const char* idKey) {
  ParkingView v;
  v.id = p[idKey] | 0u;
  v.name = panelText(p["parking_name"] | "");
  v.address = panelText(p["address"] | "");
  v.total = p["total_spaces"] | 0;
  v.available = p["available_spaces"] | 0;
  v.occupied = p["occupied_spaces"] | 0;
  v.state = p["state"] | "OPEN";
  v.notice = panelText(p["notice"] | "");
  return v;
}

}  // namespace

bool parsePanelData(JsonDocument& doc, PanelData& out, int defaultRotation) {
  PanelData data;
  if (doc["parkings"].is<JsonArrayConst>()) {
    data.avenue = true;
    int rotation = doc["rotation_seconds"] | defaultRotation;
    data.rotationSeconds = rotation >= 5 && rotation <= 60 ? rotation : defaultRotation;
    for (JsonVariantConst p : doc["parkings"].as<JsonArrayConst>()) {
      data.parkings.push_back(readParking(p, "parking_id"));
    }
  } else if (doc["parking_id"].is<uint32_t>()) {
    data.avenue = false;
    data.parkingId = doc["parking_id"];
    data.parkings.push_back(readParking(doc.as<JsonVariantConst>(), "parking_id"));
  } else {
    return false;
  }
  data.valid = true;
  data.receivedAt = millis();
  out = data;
  return true;
}

String panelText(const String& utf8) {
  // Letras con tilde en UTF-8 (2 bytes, empiezan con 0xC3) -> letra sin tilde
  static const char* const from[] = {"á", "é", "í", "ó", "ú", "Á", "É", "Í", "Ó", "Ú", "ñ", "Ñ", "ü", "Ü"};
  static const char to[] = {'a', 'e', 'i', 'o', 'u', 'A', 'E', 'I', 'O', 'U', 'n', 'N', 'u', 'U'};
  String s = utf8;
  for (size_t i = 0; i < sizeof(to); i++) {
    s.replace(from[i], String(to[i]));
  }
  // Cualquier otro carácter fuera de ASCII se quita
  String clean;
  clean.reserve(s.length());
  for (size_t i = 0; i < s.length(); i++) {
    char c = s[i];
    if (static_cast<unsigned char>(c) < 0x80) clean += c;
  }
  clean.toUpperCase();
  clean.trim();
  return clean;
}
