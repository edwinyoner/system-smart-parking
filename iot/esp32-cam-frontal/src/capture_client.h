// Envía la foto a iot-service y devuelve qué hacer con la barrera.
#pragma once

#include <Arduino.h>
#include <SmartParkingCommon.h>

#include "esp_camera.h"

struct CaptureResult {
  bool delivered = false;   // el servidor recibió y terminó de procesar la captura
  bool openGate = false;    // levantar la barrera
  int httpStatus = 0;
  String captureId;
  // ENTRY_REGISTERED, EXIT_REGISTERED, PAYMENT_REQUIRED, PENDING_REVIEW, REJECTED
  String outcome;
  String message;           // texto para el conductor u operador
  String plate;
  float confidence = 0;
};

class CaptureClient {
 public:
  explicit CaptureClient(sp::SpHttp& http) : http_(http) {}

  // captureType: "ENTRY" o "EXIT". Espera la respuesta; si llega 202, consulta el estado
  CaptureResult send(camera_fb_t* frame, const char* deviceId, const char* captureType);

 private:
  CaptureResult parse(const sp::HttpResult& response);
  CaptureResult waitResult(const String& captureId);

  sp::SpHttp& http_;
};
