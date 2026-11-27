#include "SpUltrasonic.h"

#include <algorithm>
#include <cmath>

namespace sp {

namespace {
// 30 ms de espera = unos 5 m: más allá el HC-SR04 ya no mide
constexpr unsigned long kEchoTimeoutUs = 30000;
// Rango útil del sensor
constexpr float kMinCm = 2.0f;
constexpr float kMaxCm = 400.0f;
}  // namespace

void SpUltrasonic::begin(int trigPin, int echoPin) {
  trig_ = trigPin;
  echo_ = echoPin;
  pinMode(trig_, OUTPUT);
  digitalWrite(trig_, LOW);
  pinMode(echo_, INPUT);
}

float SpUltrasonic::readOnceCm() {
  // Pulso de 10 us en TRIG; ECHO queda en alto el tiempo de ida y vuelta del sonido
  digitalWrite(trig_, LOW);
  delayMicroseconds(2);
  digitalWrite(trig_, HIGH);
  delayMicroseconds(10);
  digitalWrite(trig_, LOW);
  unsigned long us = pulseIn(echo_, HIGH, kEchoTimeoutUs);
  if (us == 0) return NAN;
  // El sonido recorre unos 0.0343 cm/us; se divide entre 2 por la ida y la vuelta
  float cm = us * 0.0343f / 2.0f;
  return (cm < kMinCm || cm > kMaxCm) ? NAN : cm;
}

float SpUltrasonic::readCm(uint8_t samples) {
  if (samples == 0) samples = 1;
  float values[15];
  uint8_t n = 0;
  samples = std::min<uint8_t>(samples, 15);
  for (uint8_t i = 0; i < samples; i++) {
    float cm = readOnceCm();
    if (!std::isnan(cm)) values[n++] = cm;
    // Pausa entre mediciones para que el eco anterior se apague
    delay(60);
  }
  if (n == 0) return NAN;
  std::sort(values, values + n);
  return values[n / 2];
}

}  // namespace sp
