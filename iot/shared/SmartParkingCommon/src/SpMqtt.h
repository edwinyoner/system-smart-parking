// Cliente MQTT con reconexión automática (con espera creciente entre intentos).
// El usuario MQTT es el deviceId: Mosquitto solo le deja escribir o leer sus propios temas.
#pragma once

#include <Arduino.h>
#include <PubSubClient.h>
#include <WiFiClient.h>
#include <WiFiClientSecure.h>

#include <functional>
#include <vector>

namespace sp {

class SpMqtt {
 public:
  using MessageHandler = std::function<void(const char* topic, const uint8_t* payload, size_t length)>;

  // port 1883 = sin TLS; 8883 con rootCa = TLS. bufferSize: tamaño máximo de un mensaje
  void begin(const char* host, uint16_t port, const char* username, const char* password,
             const char* rootCa = nullptr, uint16_t bufferSize = 1024);

  void onMessage(MessageHandler handler);

  // Temas a los que suscribirse; se renuevan en cada reconexión
  void subscribe(const String& topic);
  void clearSubscriptions();

  bool publish(const String& topic, const String& payload, bool retained = false);

  // Llamar en loop(): mantiene la conexión y recibe mensajes
  void loop();

  bool connected();

 private:
  bool connect();

  WiFiClient plain_;
  WiFiClientSecure tls_;
  PubSubClient client_;
  String host_;
  String username_;
  String password_;
  std::vector<String> topics_;
  MessageHandler handler_;
  uint32_t lastAttempt_ = 0;
  uint32_t retryDelay_ = 2000;
};

}  // namespace sp
