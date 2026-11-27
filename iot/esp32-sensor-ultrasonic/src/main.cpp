// Sensor de un espacio - Smart Parking, Winner Systems
//
// Mide la distancia con el HC-SR04 (montado arriba o al frente del espacio) y la publica en
// smartparking/sensors/{deviceId}/reading como {"distance_cm": 42.5}. iot-service decide si el
// espacio está ocupado, filtra el ruido y avisa a parking-service; el sensor solo mide.

#include <Arduino.h>
#include <SmartParkingCommon.h>

#include "config.h"

#if __has_include("secrets.h")
#include "secrets.h"
#else
#error "Falta include/secrets.h: copia include/secrets.example.h como secrets.h y completa los datos"
#endif

#ifndef SP_MQTT_ROOT_CA
#define SP_MQTT_ROOT_CA nullptr
#endif

namespace {

sp::SpWifi wifi;
sp::SpMqtt mqtt;
sp::SpUltrasonic sensor;

const String topic = String("smartparking/sensors/") + SP_DEVICE_ID + "/reading";

uint32_t lastRead = 0;
uint32_t lastPublish = 0;
uint32_t changedAt = 0;     // último cambio: se publica seguido durante FAST_WINDOW_MS
int lastOccupied = -1;      // -1 = aún sin lectura

void publish(float cm) {
  char payload[48];
  snprintf(payload, sizeof(payload), "{\"distance_cm\":%.1f}", cm);
  if (mqtt.publish(topic, payload)) {
    lastPublish = millis();
    SP_LOG("SENS", "Publicado %s", payload);
  }
}

}  // namespace

void setup() {
  Serial.begin(115200);
  delay(300);
  SP_LOG("MAIN", "Sensor %s iniciando", SP_DEVICE_ID);
  if (PIN_STATUS_LED >= 0) pinMode(PIN_STATUS_LED, OUTPUT);

  sensor.begin(PIN_TRIG, PIN_ECHO);
  wifi.begin(SP_WIFI_SSID, SP_WIFI_PASSWORD, SP_DEVICE_ID);
  wifi.waitConnected(20000);
  mqtt.begin(SP_MQTT_HOST, SP_MQTT_PORT, SP_DEVICE_ID, SP_MQTT_PASSWORD, SP_MQTT_ROOT_CA, 256);
}

void loop() {
  wifi.ensure();
  mqtt.loop();
  if (PIN_STATUS_LED >= 0) digitalWrite(PIN_STATUS_LED, mqtt.connected() ? HIGH : LOW);

  uint32_t now = millis();
  if (now - lastRead < READ_INTERVAL_MS) {
    delay(20);
    return;
  }
  lastRead = now;

  float cm = sensor.readCm(5);
  if (isnan(cm)) {
    // Sin eco: sensor desconectado, obstruido o fuera de rango. No se publica basura
    SP_LOG("SENS", "Lectura no valida");
    return;
  }

  int occupied = cm <= LOCAL_OCCUPIED_MAX_CM ? 1 : 0;
  if (occupied != lastOccupied) {
    SP_LOG("SENS", "%.1f cm: parece %s", cm, occupied ? "ocupado" : "libre");
    lastOccupied = occupied;
    changedAt = now;
    publish(cm);
    return;
  }
  // Restas de millis(): siguen bien aunque el contador vuelva a cero a los 49 días
  uint32_t interval = now - changedAt < FAST_WINDOW_MS ? PUBLISH_FAST_MS : PUBLISH_IDLE_MS;
  if (lastPublish == 0 || now - lastPublish >= interval) {
    publish(cm);
  }
}
