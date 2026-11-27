# ia-service

Servicio de inteligencia artificial en **Python 3.12** con **FastAPI**, puerto **8084**.
Reconoce placas vehiculares y detecta la ocupación de espacios con Deep Learning (YOLO26 +
EasyOCR sobre PyTorch).

- **Solo inferencia**: usa modelos ya entrenados. El entrenamiento, los datasets y la
  configuración de polígonos van en la carpeta [`ai/`](../../../ai/README.md) del monorepo.
- **Sin estado y sin lógica de negocio**: recibe una imagen y devuelve una lectura. Nunca llama
  a parking-service; quien decide qué hacer con la lectura es iot-service y, al final,
  parking-service (confianza mínima 0.85).
- **No es público**: solo lo llama iot-service por la red interna.

Explicación de los modelos, el pipeline y sus limitaciones para la tesis:
[`DEEP_LEARNING.md`](DEEP_LEARNING.md).

---

## Endpoints

Prefijo `/api/v1` (documentación interactiva en http://localhost:8084/api/docs).

| Método y ruta | Descripción |
|---|---|
| `POST /api/v1/recognitions` | Escenario A: lee la placa de una foto (`multipart/form-data`: `image`, `capture_id` opcional, umbrales opcionales) |
| `GET /api/v1/recognitions/status` | Estado de los modelos de reconocimiento |
| `POST /api/v1/parking-vision` | Escenario B: ocupación de cada espacio en una imagen aérea con sus polígonos |
| `POST /api/v1/parking-vision/validate-polygons` | Valida los polígonos generados en `ai/parking-configuration` |
| `GET /api/v1/parking-vision/health` | Salud de la visión aérea |
| `GET /api/health` | Salud general (incluye el estado de MongoDB) |

Respuesta del reconocimiento: `status` (`SUCCESS`, `NO_PLATE_DETECTED`, `OCR_FAILED`,
`LOW_CONFIDENCE`, `INVALID_FORMAT`, `ERROR`), `plate_number` (`ABC-123`), `detection`, `ocr`
(texto y confianza) y `processing_time_ms`.

---

## Tecnologías

| Componente | Versión |
|---|---|
| FastAPI / Uvicorn | 0.115.5 / 0.32.1 |
| PyTorch | CPU |
| Ultralytics (YOLO26, modelo `yolo26n.pt`) | 8.4.220 |
| EasyOCR | 1.7.2 |
| MongoDB (Motor / PyMongo) | 3.6.0 / 4.9.2 |

---

## Persistencia mínima en MongoDB

Colección `plate_recognitions` (base definida en `MONGODB_DB_NAME`; en desarrollo
`ia_service_db`):

```javascript
{ captureId, detectedPlate, confidence, processingTimeMs, status, createdAt }
```

- Sin imágenes, cajas ni texto crudo del OCR.
- `captureId` único: un reintento de la misma captura actualiza el mismo documento.
- Índice TTL: se borra a los `MONGODB_RETENTION_DAYS` días (90), porque una placa es un dato
  personal.
- Se guarda en segundo plano y es opcional: si MongoDB no responde, el reconocimiento sigue
  funcionando.

---

## Instalación y ejecución

```bash
cd backend/services/ia-service
python3.12 -m venv .venv
.venv/bin/pip install -r requirements.txt        # requirements-dev.txt para las pruebas
cp .env.example .env                             # no se versiona

.venv/bin/python -m app.main                     # http://localhost:8084
curl -s localhost:8084/api/health
```

Variables principales de `.env`: `APP_PORT` (8084), `MONGODB_URL`, `MONGODB_DB_NAME`,
`MONGODB_ENABLED`, `MONGODB_RETENTION_DAYS`, `YOLO_MODEL_PATH` (`yolo26n.pt`, modelo general:
vehículos para la ocupación aérea), `YOLO_PLATE_MODEL_PATH` (modelo de placas entrenado con
`ai/plate-training`; vacío = usa el general), `PRELOAD_MODELS` (carga YOLO y EasyOCR al arrancar).

Tras actualizar `requirements.txt` (Ultralytics 8.4): `.venv/bin/pip install -r requirements.txt`.

> Si mueves la carpeta del proyecto, el entorno `.venv` se rompe: vuelve a crearlo.

---

## Pruebas

```bash
.venv/bin/pip install -r requirements-dev.txt
.venv/bin/python -m pytest
```

Las pruebas reemplazan el servicio de reconocimiento y el repositorio con
`app.dependency_overrides`: no cargan los modelos ni necesitan MongoDB.

---

## Estructura

```text
app/
  main.py                    FastAPI con lifespan (@asynccontextmanager): MongoDB y precarga de modelos
  core/config.py             settings centralizado (from app.core.config import settings)
  api/v1/                    recognition.py, parking_vision.py
  ml/dl/yolo/detector.py     YOLO26
  ml/dl/ocr/reader.py        EasyOCR
  ml/dl/parking/             occupancy_detector.py (escenario B)
  services/                  plate_recognition_service.py, parking_vision_service.py
  database/                  mongodb.py, recognition_repository.py
  schemas/, utils/           modelos de respuesta, validadores, imágenes
scripts/                     download_models.py, test_plate_recognition.py
tests/                       pytest
yolo26n.pt                   pesos del modelo
```

## Limitaciones

`yolo26n.pt` es el modelo general de COCO (no tiene una clase "placa") y no está entrenado con
placas peruanas. El ajuste fino con un dataset propio queda como trabajo futuro, en `ai/`.
