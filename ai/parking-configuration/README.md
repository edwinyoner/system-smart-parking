# Configuración de polígonos de estacionamiento

Herramienta local para dibujar **un polígono por espacio** sobre la foto de la cámara aérea de un
parqueo abierto (escenario B). ia-service usa esos polígonos para decidir si cada espacio está
libre u ocupado: si el centro de un vehículo detectado cae dentro del polígono, está ocupado.

Se usa **una vez por zona** (o cuando cambie la cámara o las líneas pintadas). No se despliega.

## Por qué hay tres pasos

La ventana de Ultralytics (`ParkingPtsSelection`) solo guarda los puntos, **sin nombre**:

```json
[{"points": [[10, 12], [110, 12], [110, 112], [10, 112]]}, ...]
```

ia-service necesita, en cambio, el **código del espacio en parking-service** como clave, porque
con ese código se actualiza la ocupación de cada espacio:

```json
{"A-01": [[10, 12], [110, 12], [110, 112], [10, 112]], "A-02": [...]}
```

Por eso: **dibujar → convertir (poner códigos) → validar**.

## 0. Preparar

```bash
cd ai/parking-configuration/scripts
python3.12 -m venv .venv && .venv/bin/pip install -r requirements.txt
```

La ventana usa **Tkinter**. En macOS con Python de Homebrew: `brew install python-tk@3.12`.

Foto: la misma vista que tendrá la cámara instalada (misma posición, ángulo y **resolución**:
los polígonos están en píxeles), con el parqueo vacío y buena luz. Guárdala en `images/`
(no se sube a Git).

## 1. Dibujar

```bash
.venv/bin/python configure_polygons.py
```

*Upload Image* → 4 clics por espacio, en sus esquinas y **siempre en el mismo sentido** →
*Save*. *Remove Last Bounding Box* deshace el último. Queda `output/bounding_boxes.json`.

## 2. Poner los códigos

```bash
.venv/bin/python convert_polygons.py \
  --input ../output/bounding_boxes.json \
  --prefix A- --start 1 --digits 2 \
  --image ../images/parqueo-1.jpg \
  --output ../output/parking-1-zone-A.json
```

- **Códigos:** `--prefix/--start/--digits` genera `A-01, A-02...`, igual que el lote de espacios
  del panel web. También `--codes A-01,A-02,B-01` o `--codes-file codigos.txt` (uno por línea).
- **Orden:** por defecto `--order rows`: filas de arriba hacia abajo y, en cada fila, de izquierda
  a derecha, sin importar en qué orden se dibujaron. `--order clicks` respeta el orden del dibujo.
- Con `--image` genera `parking-1-zone-A-preview.jpg`: la foto con cada código sobre su espacio.
  **Revísala**: es la forma de confirmar que cada código quedó donde corresponde.
- Si hay errores no guarda nada. No sobrescribe sin `--force`.

## 3. Validar

```bash
.venv/bin/python validate_polygons.py --json ../output/parking-1-zone-A.json \
  --image ../images/parqueo-1.jpg --codes-file codigos.txt
```

| Revisión | Nivel |
|---|---|
| Formato de ia-service (si es el de Ultralytics, pide convertirlo) | Error |
| Código como en parking-service: 1-20 caracteres, mayúsculas, números, `-` o `_`; sin repetidos | Error |
| Al menos 3 puntos numéricos, sin coordenadas negativas, dentro de la imagen (`--image`) | Error |
| Área mínima de 100 px² (`--min-area`) | Error |
| Lados que se cruzan (esquinas marcadas en desorden) | Error |
| Superposición con otro espacio: desde el 10 % | Error |
| Superposición desde el 2 % | Aviso |
| Con `--codes-file`: faltan espacios de la zona o sobran | Error |

Termina con código 0 si no hay errores. `--preview salida.jpg` dibuja la foto con los polígonos.
ia-service también valida en `POST /api/v1/parking-vision/validate-polygons`.

Para `codigos.txt`, copia los códigos de la zona desde el panel web (detalle de la zona).

## 4. Uso

Los polígonos viajan junto con cada imagen aérea en `POST /api/v1/parking-vision` de ia-service.

**Pendiente:** guardar los polígonos de cada zona en el sistema (hoy ningún servicio los guarda)
y el flujo completo cámara aérea → iot-service → ia-service → parking-service (ver
[`iot/esp32-cam-aerial`](../../iot/esp32-cam-aerial/README.md)).

## Archivos

| Archivo | Qué hace |
|---|---|
| `scripts/configure_polygons.py` | Abre la ventana de Ultralytics y guarda en `output/` |
| `scripts/convert_polygons.py` | Ordena, pone códigos, valida y genera la vista previa |
| `scripts/validate_polygons.py` | Valida un JSON en el formato de ia-service |
| `scripts/polygon_tools.py` | Funciones comunes (formatos, orden, geometría, validación, vista previa) |
| `tests/` | Pruebas (no necesitan Ultralytics ni Tkinter) |

```bash
cd ai/parking-configuration
python3.12 -m venv .venv-test && .venv-test/bin/pip install -r scripts/requirements-dev.txt
.venv-test/bin/python -m pytest -q tests
```

## Qué se sube a Git

Las fotos (`images/`), las vistas previas y `bounding_boxes.json` **no** (`ai/.gitignore`): pueden
mostrar placas o personas. Los JSON finales con códigos sí: son configuración.

## Tecnologías

Python 3.12, Ultralytics (`solutions.ParkingPtsSelection`), OpenCV, Pillow, NumPy, Tkinter, pytest.
