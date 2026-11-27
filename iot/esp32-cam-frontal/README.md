# Cámara de entrada o salida (escenario A)

**Placa:** Freenove ESP32-S3 WROOM CAM (ESP32-S3 con PSRAM octal, N8R8 o N16R8, cámara OV2640).

## Qué hace

1. El **HC-SR04** detecta un vehículo a menos de 120 cm y espera 0.8 s a que se detenga.
2. Toma una foto JPEG (1024x768) y la envía a iot-service por el gateway, en base64.
3. iot-service la guarda, pide la placa a ia-service y registra la entrada o la salida en
   parking-service. La cámara **espera la respuesta** (hasta 15 s; si llega 202, consulta el estado).
4. Con `open_gate: true` levanta la barrera y la baja 2 s después de que el vehículo pasó
   (máximo 30 s abierta).
5. Si no abre (placa dudosa = "Por revisar", falta pagar, rechazo), la barrera sigue abajo y el
   operador decide. No se toma otra foto del mismo vehículo hasta que se retire.

**Botón BOOT** de la placa: pulsación corta = tomar foto ahora (prueba sin sensor); mantener 2 s
= abrir la barrera a mano (operador).

**LED:** encendido = listo; apagado = procesando o sin WiFi; tres parpadeos = error (ver el monitor).

## Conexiones

| Componente | Pin del componente | Pin de la placa |
|---|---|---|
| HC-SR04 | VCC / GND | 5V / GND |
| | TRIG | GPIO 21 |
| | ECHO | GPIO 47, **con divisor de voltaje** (1 kOhm en serie y 2 kOhm a GND): el ECHO da 5 V y el ESP32 admite 3.3 V |
| Servo SG90 (barrera de maqueta) | Señal (naranja) | GPIO 14 |
| | Rojo / marrón | 5V / GND (mejor una fuente aparte, con GND común) |
| Relé (barrera real, `GATE_MODE GATE_RELAY`) | IN | GPIO 14 |

Alimentación: **5 V y al menos 2 A**. La cámara y el WiFi juntos tienen picos de consumo; con una
fuente débil la placa se reinicia sola.

Los pines están en `include/config.h`. Comprueba el pinout de tu placa antes de conectar.

## Configurar y grabar

```bash
cd iot/esp32-cam-frontal
cp include/secrets.example.h include/secrets.h   # WiFi, URL del gateway, deviceId, ENTRY o EXIT
pio run -t upload && pio device monitor
```

`SP_CAPTURE_TYPE`: `ENTRY` en la cámara de entrada, `EXIT` en la de salida. El dispositivo debe
existir en iot-service como `ESP32_CAM_FRONTAL`, con su parqueo y su zona.

## Archivos

| Archivo | Qué hace |
|---|---|
| `src/main.cpp` | Máquina de estados: esperar vehículo, procesar, barrera abierta, esperar que se retire |
| `src/camera.*` | Inicia la OV2640 y toma una foto reciente (descarta la del búfer) |
| `src/capture_client.*` | Arma el JSON en la PSRAM, lo envía y lee la respuesta (y el estado si es 202) |
| `src/gate.*` | Servo (señal PWM de 50 Hz) o relé |
| `include/config.h` | Pines, resolución, distancias y tiempos |

## Ajustes útiles

- `CAM_FRAME_SIZE` / `CAM_JPEG_QUALITY`: más resolución ayuda a leer la placa, pero la foto tarda
  más en subir.
- `VEHICLE_PRESENT_CM`: según dónde se monte el sensor.
- Prueba sin servidor: el monitor muestra el tamaño de la foto y el error HTTP.
