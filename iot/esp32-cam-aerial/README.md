# Cámara aérea (escenario B) - pendiente

**Placa prevista:** Freenove ESP32-S3 WROOM CAM con cámara **OV5640** (5 MP) y lente gran angular
(verificar al comprar que el módulo tenga el conector DVP de 24 pines de la placa).

## Qué hará

Tomar una foto del parqueo abierto cada pocos segundos y enviarla a iot-service, que la pasa a
ia-service (`POST /api/v1/parking-vision`, YOLO con un polígono por espacio) y actualiza la
ocupación de cada espacio en parking-service.

## Por qué aún no hay firmware

Falta la parte del backend que la recibiría:

- iot-service: las rutas de `snapshots` están comentadas en `internal/api/router.go`, y hoy una
  cámara aérea en `POST /captures` recibe 403 (solo se admiten cámaras frontales).
- parking-service: no existe el endpoint de ocupación masiva al que llama
  `parking_client.go` (`BulkUpdateOccupancy`, marcado PENDIENTE).

Cuando existan, el firmware reutiliza casi todo de `esp32-cam-frontal` (cámara, HTTP y librería
común) sin barrera ni sensor: una foto periódica.

Los polígonos de cada espacio se dibujan una sola vez con
`ai/parking-configuration/scripts/configure_polygons.py`.
