// Panel LED de disponibilidad - Smart Parking, Winner Systems
//
// Dos tipos de panel, según cómo esté registrado en iot-service:
//   - Junto al parqueo (con parking_id): nombre, TOTAL, DISPONIBLES y OCUPADOS de ese parqueo.
//   - De avenida (sin parking_id): rota los parqueos, uno cada rotation_seconds, con dirección.
//
// Al encender pregunta por HTTP qué mostrar; luego recibe los cambios al instante por MQTT
// (mensaje retenido: al conectarse ya llega el último dato). Si MQTT no trae datos, vuelve a
// preguntar por HTTP cada 10 s. Sin datos recientes muestra "SIN CONEXION", no números viejos.

#include <Arduino.h>
#include <ArduinoJson.h>
#include <SmartParkingCommon.h>

#include "config.h"
#include "display.h"
#include "panel_data.h"

#if __has_include("secrets.h")
#include "secrets.h"
#else
#error "Falta include/secrets.h: copia include/secrets.example.h como secrets.h y completa los datos"
#endif

#ifndef SP_ROOT_CA
#define SP_ROOT_CA nullptr
#endif

namespace {

sp::SpWifi wifi;
sp::SpHttp http;
sp::SpMqtt mqtt;

PanelData data;
String subscribedTopic;
uint32_t lastHttp = 0;
uint32_t lastModeCheck = 0;
size_t rotationIndex = 0;
uint32_t rotationSince = 0;

// Tema MQTT que corresponde al panel según su tipo
String topicFor(const PanelData& d) {
  if (d.avenue) return String("smartparking/panels/") + SP_DEVICE_ID + "/availability";
  return String("smartparking/parkings/") + d.parkingId + "/availability";
}

void applyData(const PanelData& fresh, const char* source) {
  bool modeChanged = !data.valid || fresh.avenue != data.avenue || fresh.parkingId != data.parkingId;
  data = fresh;
  SP_LOG("DATA", "Datos por %s: %s, %u parqueo(s)", source, data.avenue ? "avenida" : "parqueo",
         (unsigned)data.parkings.size());
  if (rotationIndex >= data.parkings.size()) rotationIndex = 0;
  if (modeChanged) {
    String topic = topicFor(data);
    if (topic != subscribedTopic) {
      mqtt.clearSubscriptions();
      mqtt.subscribe(topic);
      subscribedTopic = topic;
    }
  }
}

// POST /displays/availability: además de los datos, dice si el panel es de parqueo o de avenida
bool fetchHttp() {
  lastHttp = millis();
  String body = String("{\"device_id\":\"") + SP_DEVICE_ID + "\"}";
  sp::HttpResult r = http.postJson("/displays/availability", body, 10000);
  if (!r.ok()) {
    SP_LOG("DATA", "HTTP %d: %s", r.status, r.body.substring(0, 120).c_str());
    return false;
  }
  JsonDocument doc;
  PanelData fresh;
  if (deserializeJson(doc, r.body) != DeserializationError::Ok ||
      !parsePanelData(doc, fresh, DEFAULT_ROTATION_SECONDS)) {
    SP_LOG("DATA", "Respuesta HTTP no valida");
    return false;
  }
  applyData(fresh, "HTTP");
  lastModeCheck = millis();
  return true;
}

void onMqtt(const char* topic, const uint8_t* payload, size_t length) {
  JsonDocument doc;
  PanelData fresh;
  if (deserializeJson(doc, payload, length) != DeserializationError::Ok ||
      !parsePanelData(doc, fresh, DEFAULT_ROTATION_SECONDS)) {
    SP_LOG("DATA", "Mensaje MQTT no valido en %s", topic);
    return;
  }
  applyData(fresh, "MQTT");
}

// Elige qué mostrar: mensaje, el parqueo, o el turno de la rotación
void render() {
  uint32_t now = millis();
  if (!data.valid) {
    display::showMessage("SMART PARKING", wifi.connected() ? "CONECTANDO" : "SIN RED");
    return;
  }
  if (now - data.receivedAt > STALE_AFTER_MS) {
    String name = data.parkings.empty() || data.avenue ? String("SMART PARKING") : data.parkings[0].name;
    display::showMessage(name, "SIN CONEXION");
    return;
  }
  if (data.parkings.empty()) {
    display::showMessage("SMART PARKING", "SIN PARQUEOS");
    return;
  }
  if (!data.avenue) {
    display::showParking(data.parkings[0], false, 0, 0);
    return;
  }
  if (now - rotationSince >= (uint32_t)data.rotationSeconds * 1000) {
    rotationSince = now;
    rotationIndex = (rotationIndex + 1) % data.parkings.size();
  }
  display::showParking(data.parkings[rotationIndex], true, rotationIndex + 1, data.parkings.size());
}

}  // namespace

void setup() {
  Serial.begin(115200);
  delay(300);
  SP_LOG("MAIN", "Panel %s iniciando", SP_DEVICE_ID);

  display::begin();
  display::showMessage("SMART PARKING", "INICIANDO");

  wifi.begin(SP_WIFI_SSID, SP_WIFI_PASSWORD, SP_DEVICE_ID);
  wifi.waitConnected(20000);
  http.begin(SP_API_BASE_URL, SP_DEVICE_ID, SP_ROOT_CA);
  // Un panel de avenida con muchos parqueos manda mensajes largos: búfer de 8 KB
  mqtt.begin(SP_MQTT_HOST, SP_MQTT_PORT, SP_DEVICE_ID, SP_MQTT_PASSWORD, SP_ROOT_CA, 8192);
  mqtt.onMessage(onMqtt);

  if (wifi.connected()) fetchHttp();
}

void loop() {
  wifi.ensure();
  mqtt.loop();
  uint32_t now = millis();

  // Respaldo HTTP: sin datos todavía, MQTT caído, o datos sin renovar hace rato
  bool needHttp = !data.valid || !mqtt.connected() || now - data.receivedAt > STALE_AFTER_MS / 2;
  if (wifi.connected() && needHttp && now - lastHttp >= HTTP_POLL_MS) {
    fetchHttp();
  } else if (wifi.connected() && now - lastModeCheck >= RECHECK_MODE_MS && now - lastHttp >= HTTP_POLL_MS) {
    // Comprobación periódica por si cambió la configuración del panel
    fetchHttp();
  }

  render();
  display::tick();
  delay(5);
}
