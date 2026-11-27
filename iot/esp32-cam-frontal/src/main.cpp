// Cámara de entrada o salida (escenario A) - Smart Parking, Winner Systems
//
// 1. El HC-SR04 detecta un vehículo frente a la cámara y espera a que se detenga.
// 2. Toma la foto y la envía a iot-service (por el api-gateway), que la sube al almacenamiento,
//    pide la placa a ia-service y registra la entrada o la salida en parking-service.
// 3. Si la respuesta trae open_gate = true, levanta la barrera y la baja cuando el vehículo pasó.
//    Si no (placa dudosa, falta pagar...), la barrera sigue abajo y el operador decide.
//
// La placa no reconoce placas: solo captura y envía. La IA corre en ia-service.

#include <Arduino.h>
#include <SmartParkingCommon.h>

#include "camera.h"
#include "capture_client.h"
#include "config.h"
#include "gate.h"

#if __has_include("secrets.h")
#include "secrets.h"
#else
#error "Falta include/secrets.h: copia include/secrets.example.h como secrets.h y completa los datos"
#endif

#ifndef SP_ROOT_CA
#define SP_ROOT_CA nullptr
#endif

namespace {

enum class State {
  Idle,          // esperando un vehículo
  GateOpen,      // barrera arriba, esperando que el vehículo pase
  WaitLeave      // ya se procesó; esperar que el vehículo se retire antes de otra foto
};

sp::SpWifi wifi;
sp::SpHttp http;
sp::SpUltrasonic sensor;
CaptureClient captures(http);
Gate gate;

State state = State::Idle;
uint32_t presentSince = 0;   // desde cuándo hay un vehículo detenido frente a la cámara
uint32_t clearSince = 0;     // desde cuándo el sensor ya no ve el vehículo
uint32_t lastCaptureAt = 0;
uint32_t buttonDownAt = 0;
bool cameraReady = false;

void setLed(bool on) {
  if (PIN_STATUS_LED >= 0) digitalWrite(PIN_STATUS_LED, on ? HIGH : LOW);
}

// Tres parpadeos rápidos: algo salió mal (ver el monitor serie)
void blinkError() {
  for (int i = 0; i < 3; i++) {
    setLed(false);
    delay(120);
    setLed(true);
    delay(120);
  }
}

bool vehiclePresent() {
  float cm = sensor.readCm(3);
  return !isnan(cm) && cm < VEHICLE_PRESENT_CM;
}

// Toma la foto, la envía y actúa según la respuesta
void captureAndProcess() {
  lastCaptureAt = millis();
  if (!cameraReady || !wifi.connected()) {
    SP_LOG("MAIN", "No se puede capturar: %s", cameraReady ? "sin WiFi" : "camara no iniciada");
    blinkError();
    state = State::WaitLeave;
    return;
  }
  setLed(false);  // LED apagado = procesando
  camera_fb_t* frame = cam::capture();
  if (frame == nullptr) {
    setLed(true);
    blinkError();
    state = State::WaitLeave;
    return;
  }
  CaptureResult r = captures.send(frame, SP_DEVICE_ID, SP_CAPTURE_TYPE);
  cam::release(frame);
  setLed(true);

  SP_LOG("MAIN", "Resultado: %s | placa %s (%.0f %%) | %s", r.outcome.length() ? r.outcome.c_str() : "-",
         r.plate.length() ? r.plate.c_str() : "-", r.confidence * 100, r.message.c_str());

  if (r.openGate) {
    gate.open();
    clearSince = 0;
    state = State::GateOpen;
    return;
  }
  // PENDING_REVIEW: el operador la revisa en "Por revisar" y abre a mano (botón largo)
  // PAYMENT_REQUIRED: el conductor paga antes de salir
  if (!r.delivered) blinkError();
  state = State::WaitLeave;
}

// Botón BOOT: corto = tomar foto ahora; largo = abrir la barrera (operador)
void handleButton() {
  bool down = digitalRead(PIN_BUTTON) == LOW;
  if (down && buttonDownAt == 0) {
    buttonDownAt = millis();
  } else if (!down && buttonDownAt != 0) {
    uint32_t held = millis() - buttonDownAt;
    buttonDownAt = 0;
    if (held >= BUTTON_LONG_PRESS_MS) {
      SP_LOG("MAIN", "Apertura manual de la barrera");
      gate.open();
      clearSince = 0;
      state = State::GateOpen;
    } else if (held > 50) {
      SP_LOG("MAIN", "Captura manual (boton)");
      captureAndProcess();
    }
  }
}

}  // namespace

void setup() {
  Serial.begin(115200);
  delay(300);
  SP_LOG("MAIN", "Camara %s (%s) iniciando", SP_DEVICE_ID, SP_CAPTURE_TYPE);

  if (PIN_STATUS_LED >= 0) pinMode(PIN_STATUS_LED, OUTPUT);
  pinMode(PIN_BUTTON, INPUT_PULLUP);
  setLed(false);

  gate.begin();
  sensor.begin(PIN_TRIG, PIN_ECHO);
  cameraReady = cam::begin();

  wifi.begin(SP_WIFI_SSID, SP_WIFI_PASSWORD, SP_DEVICE_ID);
  wifi.waitConnected(20000);
  http.begin(SP_API_BASE_URL, SP_DEVICE_ID, SP_ROOT_CA);

  setLed(cameraReady && wifi.connected());
  SP_LOG("MAIN", "Listo. Esperando vehiculos (menos de %d cm)", VEHICLE_PRESENT_CM);
}

void loop() {
  wifi.ensure();
  handleButton();
  bool present = vehiclePresent();
  uint32_t now = millis();

  switch (state) {
    case State::Idle:
      if (!present) {
        presentSince = 0;
      } else if (presentSince == 0) {
        presentSince = now;
      } else if (now - presentSince >= VEHICLE_STABLE_MS) {
        presentSince = 0;
        captureAndProcess();
      }
      break;

    case State::GateOpen:
      // Se baja cuando el vehículo ya pasó (el sensor queda libre un momento) o por tiempo
      if (present) {
        clearSince = 0;
      } else if (clearSince == 0) {
        clearSince = now;
      }
      if ((clearSince != 0 && now - clearSince >= GATE_CLOSE_DELAY_MS) || gate.openFor() >= GATE_MAX_OPEN_MS) {
        gate.close();
        state = present ? State::WaitLeave : State::Idle;
      }
      break;

    case State::WaitLeave:
      // No disparar otra foto del mismo vehículo: esperar que se retire
      if (!present && now - lastCaptureAt >= CAPTURE_COOLDOWN_MS) {
        state = State::Idle;
      }
      break;
  }
  setLed(cameraReady && wifi.connected());
  delay(50);
}
