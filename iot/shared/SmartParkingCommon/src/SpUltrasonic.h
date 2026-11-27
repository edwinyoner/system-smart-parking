// Sensor ultrasónico HC-SR04. Mide varias veces y devuelve la mediana, que descarta los
// rebotes sueltos (lecturas muy cortas o muy largas que da el sensor de vez en cuando).
//
// Conexión: el HC-SR04 funciona a 5 V y su pin ECHO entrega 5 V; el ESP32 solo admite 3.3 V.
// Poner un divisor de voltaje en ECHO (por ejemplo 1 kOhm en serie y 2 kOhm a GND).
#pragma once

#include <Arduino.h>

namespace sp {

class SpUltrasonic {
 public:
  void begin(int trigPin, int echoPin);

  // Distancia en cm (mediana de `samples` mediciones). NAN si ninguna fue válida
  float readCm(uint8_t samples = 5);

 private:
  float readOnceCm();

  int trig_ = -1;
  int echo_ = -1;
};

}  // namespace sp
