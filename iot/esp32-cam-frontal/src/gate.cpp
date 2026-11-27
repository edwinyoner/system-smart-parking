#include "gate.h"

#include <SmartParkingCommon.h>

#include "config.h"

namespace {
// Servo de 50 Hz: el pulso de 0.5 ms a 2.4 ms marca el ángulo de 0 a 180 grados
constexpr uint32_t kServoHz = 50;
constexpr uint8_t kServoBits = 14;
constexpr uint32_t kPulseMinUs = 500;
constexpr uint32_t kPulseMaxUs = 2400;
}  // namespace

void Gate::begin() {
#if GATE_MODE == GATE_SERVO
  ledcAttach(PIN_GATE, kServoHz, kServoBits);
#else
  pinMode(PIN_GATE, OUTPUT);
#endif
  close();
}

void Gate::open() {
  if (open_) return;
#if GATE_MODE == GATE_SERVO
  writeServo(SERVO_OPEN_DEG);
#else
  digitalWrite(PIN_GATE, RELAY_ACTIVE_LEVEL);
#endif
  open_ = true;
  openedAt_ = millis();
  SP_LOG("GATE", "Barrera abierta");
}

void Gate::close() {
#if GATE_MODE == GATE_SERVO
  writeServo(SERVO_CLOSED_DEG);
#else
  digitalWrite(PIN_GATE, RELAY_ACTIVE_LEVEL == HIGH ? LOW : HIGH);
#endif
  if (open_) SP_LOG("GATE", "Barrera cerrada");
  open_ = false;
}

void Gate::writeServo(int degrees) {
  degrees = constrain(degrees, 0, 180);
  uint32_t pulseUs = kPulseMinUs + (kPulseMaxUs - kPulseMinUs) * degrees / 180;
  uint32_t periodUs = 1000000 / kServoHz;
  uint32_t duty = pulseUs * ((1u << kServoBits) - 1) / periodUs;
  ledcWrite(PIN_GATE, duty);
}
