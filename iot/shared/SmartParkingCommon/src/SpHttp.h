// Peticiones HTTP al api-gateway. Toda petición lleva la cabecera X-Device-ID (iot-service la
// usa para el límite de peticiones por dispositivo). Admite http:// y https://.
#pragma once

#include <Arduino.h>

namespace sp {

struct HttpResult {
  int status = 0;  // código HTTP; negativo = error de conexión (ver HTTPClient)
  String body;

  bool ok() const { return status >= 200 && status < 300; }
};

class SpHttp {
 public:
  // baseUrl: por ejemplo "http://192.168.1.50:8080/api/iot-service/v1" (sin "/" al final)
  // rootCa: certificado raíz PEM para https; nullptr = http, o https sin verificar si
  //         allowInsecureTls es true (solo para pruebas)
  void begin(const char* baseUrl, const char* deviceId, const char* rootCa = nullptr,
             bool allowInsecureTls = false);

  // POST con cuerpo JSON. timeoutMs = cuánto esperar la respuesta
  HttpResult postJson(const char* path, const uint8_t* body, size_t length, uint32_t timeoutMs);
  HttpResult postJson(const char* path, const String& body, uint32_t timeoutMs);

  HttpResult get(const char* path, uint32_t timeoutMs);

 private:
  HttpResult send(const char* method, const char* path, const uint8_t* body, size_t length,
                  uint32_t timeoutMs);

  String baseUrl_;
  String deviceId_;
  const char* rootCa_ = nullptr;
  bool allowInsecureTls_ = false;
};

}  // namespace sp
