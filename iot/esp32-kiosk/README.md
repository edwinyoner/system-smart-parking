# Kiosco de pago en la calle - pendiente

**Placa prevista:** Waveshare **ESP32-S3-Touch-LCD-4.3** (ESP32-S3 con pantalla táctil de
800x480 integrada). Interfaz con LVGL.

## Qué hará

En los parqueos de calle (sin barrera), el conductor escribe su placa (y su documento si quiere),
ve cuánto debe y paga con **QR** (Yape, Plin o banca). La pantalla muestra el QR dinámico y
confirma el pago cuando la pasarela lo avisa a payment-service.

## Por qué aún no hay firmware

Falta el backend para un dispositivo sin usuario:

- Un endpoint público y limitado del kiosco que, por placa, devuelva la deuda de calle y cree el
  cobro QR en payment-service (hoy el cobro exige un usuario con `payments.process`).
- La credencial por dispositivo (API key): un kiosco en la calle no puede quedar abierto.

Cuando existan, el firmware usa la librería común (`iot/shared`) para WiFi y HTTP.
