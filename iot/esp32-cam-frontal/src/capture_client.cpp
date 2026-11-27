#include "capture_client.h"

#include <ArduinoJson.h>

#include "config.h"
#include "mbedtls/base64.h"

namespace {

// Libera la memoria del cuerpo al salir de send(), pase lo que pase
struct PsramBuffer {
  uint8_t* data = nullptr;
  ~PsramBuffer() { free(data); }
};

}  // namespace

CaptureResult CaptureClient::send(camera_fb_t* frame, const char* deviceId, const char* captureType) {
  // Cuerpo: {"device_id":"...","capture_type":"ENTRY","image_base64":"<foto>"}
  // Se arma en la PSRAM: la foto en base64 ocupa un tercio más que el JPEG
  String head = String("{\"device_id\":\"") + deviceId + "\",\"capture_type\":\"" + captureType +
                "\",\"image_base64\":\"";
  const char* tail = "\"}";
  size_t b64Length = 4 * ((frame->len + 2) / 3);
  size_t total = head.length() + b64Length + strlen(tail);

  PsramBuffer body;
  body.data = static_cast<uint8_t*>(ps_malloc(total + 1));
  if (body.data == nullptr) {
    SP_LOG("HTTP", "Sin memoria para enviar la foto (%u bytes)", (unsigned)total);
    return {};
  }
  memcpy(body.data, head.c_str(), head.length());
  size_t written = 0;
  // +1: mbedtls escribe un \0 al final
  int err = mbedtls_base64_encode(body.data + head.length(), b64Length + 1, &written, frame->buf,
                                  frame->len);
  if (err != 0) {
    SP_LOG("HTTP", "No se pudo codificar la foto (error %d)", err);
    return {};
  }
  memcpy(body.data + head.length() + written, tail, strlen(tail));
  total = head.length() + written + strlen(tail);

  SP_LOG("HTTP", "Enviando captura %s (%u KB)...", captureType, (unsigned)(total / 1024));
  uint32_t start = millis();
  sp::HttpResult response = http_.postJson("/captures", body.data, total, CAPTURE_HTTP_TIMEOUT_MS);
  SP_LOG("HTTP", "Respuesta %d en %lu ms", response.status, (unsigned long)(millis() - start));

  CaptureResult result = parse(response);
  // 202: el servidor sigue procesando; se consulta el estado hasta tener el resultado
  if (response.status == 202 && result.captureId.length() > 0) {
    return waitResult(result.captureId);
  }
  return result;
}

CaptureResult CaptureClient::waitResult(const String& captureId) {
  uint32_t start = millis();
  String path = "/captures/" + captureId + "/status";
  while (millis() - start < STATUS_POLL_MAX_MS) {
    delay(STATUS_POLL_INTERVAL_MS);
    CaptureResult r = parse(http_.get(path.c_str(), 10000));
    // PENDING o PROCESSING: aún no hay respuesta de parking-service
    if (r.delivered) return r;
  }
  CaptureResult timeout;
  timeout.captureId = captureId;
  timeout.message = "Sin respuesta del servidor: llame al operador";
  return timeout;
}

CaptureResult CaptureClient::parse(const sp::HttpResult& response) {
  CaptureResult r;
  r.httpStatus = response.status;
  if (response.status <= 0) {
    r.message = "Sin conexion con el servidor";
    return r;
  }

  JsonDocument doc;
  if (deserializeJson(doc, response.body) != DeserializationError::Ok) {
    r.message = "Respuesta no valida del servidor (HTTP " + String(response.status) + ")";
    return r;
  }
  r.captureId = doc["capture_id"] | "";
  r.outcome = doc["outcome"] | "";
  r.message = doc["message"] | "";
  r.plate = doc["plate_number"] | "";
  r.confidence = doc["confidence"] | 0.0f;
  r.openGate = doc["open_gate"] | false;

  String status = doc["processing_status"] | "";
  // Terminado: COMPLETED (con resultado) o FAILED; en curso: PENDING o PROCESSING
  r.delivered = response.ok() && response.status != 202 && status != "PENDING" && status != "PROCESSING";
  if (!response.ok() && r.message.length() == 0) {
    r.message = String("Error HTTP ") + response.status;
  }
  return r;
}
