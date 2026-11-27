#include "SpMqtt.h"

#include <WiFi.h>

#include "SpLog.h"

namespace sp {

namespace {
constexpr uint32_t kMaxRetryDelay = 60000;
}

void SpMqtt::begin(const char* host, uint16_t port, const char* username, const char* password,
                   const char* rootCa, uint16_t bufferSize) {
  host_ = host;
  username_ = username;
  password_ = password;
  if (rootCa != nullptr) {
    tls_.setCACert(rootCa);
    client_.setClient(tls_);
  } else {
    client_.setClient(plain_);
  }
  client_.setServer(host_.c_str(), port);
  client_.setBufferSize(bufferSize);
  client_.setKeepAlive(30);
  client_.setCallback([this](char* topic, uint8_t* payload, unsigned int length) {
    if (handler_) handler_(topic, payload, length);
  });
}

void SpMqtt::onMessage(MessageHandler handler) { handler_ = std::move(handler); }

void SpMqtt::subscribe(const String& topic) {
  for (const String& t : topics_) {
    if (t == topic) return;
  }
  topics_.push_back(topic);
  if (client_.connected()) {
    client_.subscribe(topic.c_str(), 1);
  }
}

void SpMqtt::clearSubscriptions() {
  for (const String& t : topics_) {
    if (client_.connected()) client_.unsubscribe(t.c_str());
  }
  topics_.clear();
}

bool SpMqtt::publish(const String& topic, const String& payload, bool retained) {
  if (!client_.connected()) return false;
  return client_.publish(topic.c_str(), payload.c_str(), retained);
}

bool SpMqtt::connected() { return client_.connected(); }

void SpMqtt::loop() {
  if (client_.connected()) {
    client_.loop();
    return;
  }
  if (WiFi.status() != WL_CONNECTED) return;
  if (lastAttempt_ != 0 && millis() - lastAttempt_ < retryDelay_) return;
  lastAttempt_ = millis();
  if (connect()) {
    retryDelay_ = 2000;
  } else {
    // Espera creciente para no saturar el broker si está caído
    retryDelay_ = min(retryDelay_ * 2, kMaxRetryDelay);
  }
}

bool SpMqtt::connect() {
  // El clientId debe ser único en el broker: se usa el deviceId
  if (!client_.connect(username_.c_str(), username_.c_str(), password_.c_str())) {
    // state(): -2 sin red al broker, 4 usuario o clave incorrectos, 5 no autorizado
    SP_LOG("MQTT", "No se pudo conectar a %s (estado %d); reintento en %lu s", host_.c_str(),
           client_.state(), (unsigned long)(retryDelay_ / 1000));
    return false;
  }
  SP_LOG("MQTT", "Conectado a %s como %s", host_.c_str(), username_.c_str());
  for (const String& t : topics_) {
    client_.subscribe(t.c_str(), 1);
    SP_LOG("MQTT", "Suscrito a %s", t.c_str());
  }
  return true;
}

}  // namespace sp
