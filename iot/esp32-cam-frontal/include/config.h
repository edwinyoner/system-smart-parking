// Configuración de la cámara de entrada o salida. Los datos de la red y del dispositivo van
// en secrets.h; aquí solo pines y tiempos.
#pragma once

#include "esp_camera.h"

// ========================= CÁMARA (Freenove ESP32-S3 WROOM CAM) =========================
// Mismos pines que el modelo ESP32S3_EYE de los ejemplos de Espressif (lo indica Freenove)
#define CAM_PIN_PWDN -1
#define CAM_PIN_RESET -1
#define CAM_PIN_XCLK 15
#define CAM_PIN_SIOD 4
#define CAM_PIN_SIOC 5
#define CAM_PIN_D7 16
#define CAM_PIN_D6 17
#define CAM_PIN_D5 18
#define CAM_PIN_D4 12
#define CAM_PIN_D3 10
#define CAM_PIN_D2 8
#define CAM_PIN_D1 9
#define CAM_PIN_D0 11
#define CAM_PIN_VSYNC 6
#define CAM_PIN_HREF 7
#define CAM_PIN_PCLK 13

// XGA (1024x768): la placa se lee bien y la foto pesa unos 60-120 KB. Más grande = más lento
#define CAM_FRAME_SIZE FRAMESIZE_XGA
// Calidad JPEG: 0-63, menor = mejor imagen y más peso
#define CAM_JPEG_QUALITY 10

// ========================= SENSOR QUE DETECTA EL VEHÍCULO (HC-SR04) =========================
// ECHO con divisor de voltaje (ver SpUltrasonic.h)
#define PIN_TRIG 21
#define PIN_ECHO 47
// Hay vehículo frente a la cámara si la distancia es menor a esto
#define VEHICLE_PRESENT_CM 120
// El vehículo debe seguir ahí este tiempo antes de tomar la foto (que se detenga)
#define VEHICLE_STABLE_MS 800

// ========================= BARRERA =========================
// GATE_SERVO = servo SG90 de la maqueta; GATE_RELAY = relé que acciona una barrera real
#define GATE_SERVO 1
#define GATE_RELAY 2
#define GATE_MODE GATE_SERVO
#define PIN_GATE 14
// Servo: ángulos de barrera cerrada y abierta
#define SERVO_CLOSED_DEG 0
#define SERVO_OPEN_DEG 90
// Relé: nivel que lo activa (muchos módulos se activan con LOW)
#define RELAY_ACTIVE_LEVEL HIGH
// Se cierra cuando el vehículo ya pasó (sensor libre) más este margen; o como máximo a los
#define GATE_CLOSE_DELAY_MS 2000
#define GATE_MAX_OPEN_MS 30000

// ========================= LED Y BOTÓN =========================
// LED de estado (-1 = sin LED). Encendido fijo: listo; parpadeo: procesando
#define PIN_STATUS_LED 2
// Botón BOOT de la placa: pulsación corta = tomar foto (prueba sin sensor);
// mantener 2 s = abrir la barrera a mano (lo hace el operador)
#define PIN_BUTTON 0
#define BUTTON_LONG_PRESS_MS 2000

// ========================= SERVIDOR =========================
// iot-service espera el resultado hasta 15 s; se da margen para la subida de la foto
#define CAPTURE_HTTP_TIMEOUT_MS 25000
// Si responde 202 (aún procesando): consultar el estado cada tanto, hasta un máximo
#define STATUS_POLL_INTERVAL_MS 1500
#define STATUS_POLL_MAX_MS 30000
// Tras una captura, no volver a disparar hasta que el vehículo se vaya, o pasado este tiempo
#define CAPTURE_COOLDOWN_MS 5000
