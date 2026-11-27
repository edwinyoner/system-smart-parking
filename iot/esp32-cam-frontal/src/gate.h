// Barrera: servo SG90 (maqueta) o relé (barrera real), según GATE_MODE en config.h.
#pragma once

#include <Arduino.h>

class Gate {
 public:
  void begin();
  void open();
  void close();
  bool isOpen() const { return open_; }
  // Milisegundos que lleva abierta (0 si está cerrada)
  uint32_t openFor() const { return open_ ? millis() - openedAt_ : 0; }

 private:
  void writeServo(int degrees);

  bool open_ = false;
  uint32_t openedAt_ = 0;
};
