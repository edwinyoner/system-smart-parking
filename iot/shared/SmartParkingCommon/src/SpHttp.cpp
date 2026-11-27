#include "SpHttp.h"

#include <HTTPClient.h>
#include <WiFiClient.h>
#include <WiFiClientSecure.h>

#include "SpLog.h"

namespace sp {

void SpHttp::begin(const char* baseUrl, const char* deviceId, const char* rootCa,
                   bool allowInsecureTls) {
  baseUrl_ = baseUrl;
  deviceId_ = deviceId;
  rootCa_ = rootCa;
  allowInsecureTls_ = allowInsecureTls;
}

HttpResult SpHttp::postJson(const char* path, const String& body, uint32_t timeoutMs) {
  return send("POST", path, reinterpret_cast<const uint8_t*>(body.c_str()), body.length(), timeoutMs);
}

HttpResult SpHttp::postJson(const char* path, const uint8_t* body, size_t length,
                            uint32_t timeoutMs) {
  return send("POST", path, body, length, timeoutMs);
}

HttpResult SpHttp::get(const char* path, uint32_t timeoutMs) {
  return send("GET", path, nullptr, 0, timeoutMs);
}

HttpResult SpHttp::send(const char* method, const char* path, const uint8_t* body, size_t length,
                        uint32_t timeoutMs) {
  HttpResult result;
  String url = baseUrl_ + path;
  bool secure = url.startsWith("https://");

  // El cliente vive solo durante la petición: así no queda memoria tomada entre capturas
  WiFiClient plain;
  WiFiClientSecure tls;
  if (secure) {
    if (rootCa_ != nullptr) {
      tls.setCACert(rootCa_);
    } else if (allowInsecureTls_) {
      tls.setInsecure();
    } else {
      SP_LOG("HTTP", "https sin certificado raiz: define SP_ROOT_CA en secrets.h");
      result.status = -1;
      return result;
    }
  }

  HTTPClient http;
  if (!http.begin(secure ? static_cast<WiFiClient&>(tls) : plain, url)) {
    SP_LOG("HTTP", "URL no valida: %s", url.c_str());
    result.status = -1;
    return result;
  }
  http.setConnectTimeout(5000);
  http.setTimeout(timeoutMs);
  http.addHeader("X-Device-ID", deviceId_);
  if (body != nullptr) {
    http.addHeader("Content-Type", "application/json");
  }

  result.status = http.sendRequest(method, const_cast<uint8_t*>(body), length);
  if (result.status > 0) {
    result.body = http.getString();
  } else {
    SP_LOG("HTTP", "%s %s fallo: %s", method, path, HTTPClient::errorToString(result.status).c_str());
  }
  http.end();
  return result;
}

}  // namespace sp
