// Configuración del sensor de espacio. Red y credenciales en secrets.h.
#pragma once

// HC-SR04 (ECHO con divisor de voltaje: ver SpUltrasonic.h)
#define PIN_TRIG 4
#define PIN_ECHO 5

// Cada cuánto se mide (una medición = mediana de 5 lecturas, unos 0.3 s)
#define READ_INTERVAL_MS 1000

// Solo para decidir cuándo publicar seguido: el umbral real lo aplica iot-service
// (SENSOR_OCCUPIED_MAX_CM = 80). Mantener ambos iguales
#define LOCAL_OCCUPIED_MAX_CM 80

// iot-service confirma un cambio si se mantiene 5 s (antirrebote): necesita más de una
// lectura. Tras un cambio se publica cada 2 s durante 20 s; en reposo, cada 30 s
#define PUBLISH_FAST_MS 2000
#define FAST_WINDOW_MS 20000
#define PUBLISH_IDLE_MS 30000

// LED de la placa (la DevKitC-1 trae un LED RGB en el 8; -1 = sin LED)
#define PIN_STATUS_LED -1
