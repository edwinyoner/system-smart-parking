# Configuración de polígonos de estacionamiento

Herramienta local para dibujar **un polígono por espacio** sobre una foto aérea de un parqueo
abierto (escenario B). ia-service usa esos polígonos para decidir si cada espacio está libre u
ocupado: si el centro de un vehículo detectado cae dentro del polígono, el espacio está ocupado.

Se usa **una vez por parqueo** (o cuando cambie la cámara o las líneas pintadas). No se despliega.

## 1. Foto

- La misma vista que tendrá la cámara aérea instalada (misma posición, ángulo y resolución).
- Mejor con el parqueo vacío y buena luz.
- Guardarla en `images/` y no subirla a Git (puede mostrar placas o personas).

## 2. Dibujar los polígonos

```bash
cd ai/parking-configuration/scripts
python3.12 -m venv .venv && .venv/bin/pip install -r requirements.txt
.venv/bin/python configure_polygons.py
```

Se abre la herramienta `ParkingPtsSelection` de Ultralytics (ventana Tkinter): cargar la
imagen, marcar las esquinas de cada espacio y pulsar **Save**. Se genera `bounding_boxes.json`.
Moverlo a `output/` con el nombre `parking-<id>-polygons.json`.

## 3. Validar

```bash
.venv/bin/python validate_polygons.py --json ../output/parking-1-polygons.json
```

También se puede validar contra el servicio: `POST /api/v1/parking-vision/validate-polygons` de
ia-service.

## 4. Uso

Los polígonos viajan junto con cada imagen aérea en `POST /api/v1/parking-vision` de ia-service.

**Pendiente:** guardar los polígonos de cada parqueo en el sistema (hoy no hay un endpoint para
cargarlos) y el flujo completo cámara aérea → iot-service → parking-service. Cada polígono
deberá enlazarse con el código del espacio (por ejemplo `A-01`).

## Tecnologías

Python 3.12, Ultralytics (`solutions.ParkingPtsSelection`), OpenCV, Pillow, Tkinter.
