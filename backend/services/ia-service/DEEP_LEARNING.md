# Deep Learning en ia-service

Documento de apoyo para la tesis y la defensa: qué modelos usa `ia-service`, cómo se
encadenan, qué decide cada servicio y cuáles son las limitaciones conocidas.

> Prototipo académico. `ia-service` **solo hace inferencia** con modelos ya entrenados.
> El entrenamiento o ajuste de modelos, los datasets y la configuración de polígonos van en
> la carpeta `ai/` del monorepo y no se despliegan.

---

## 1. Lugar de ia-service en el sistema

```text
ESP32-CAM ──foto──► iot-service ──imagen (multipart)──► ia-service
                        │                                   │ YOLO + EasyOCR
                        │◄────── placa + confianza ─────────┘
                        │
                        └──captura──► parking-service (decide: registrar, "Por revisar" o cobrar)
```

- `ia-service` es **funcional y sin estado**: recibe una imagen, devuelve una lectura. No
  conoce estadías, tarifas ni reglas de negocio, y **nunca** llama a `parking-service`.
- La regla de negocio (confianza mínima **0.85** para registrar sin operador) vive en
  `parking-service` (`parking.ai.min-plate-confidence`). Una lectura dudosa no se descarta:
  va a la cola "Por revisar" del panel con su foto.
- El orquestador es `iot-service`.

## 2. Escenario A: reconocimiento de placas (ANPR)

Endpoint: `POST /api/v1/recognitions` (formulario `multipart/form-data`).

| Campo | Tipo | Descripción |
|---|---|---|
| `image` | archivo | JPG, JPEG o PNG, máximo 10 MB |
| `capture_id` | texto, opcional (máx. 64) | ID de la captura en iot-service; enlaza el registro en MongoDB |
| `yolo_confidence` | 0-1, opcional | Umbral de detección (por defecto 0.25) |
| `ocr_confidence` | 0-1, opcional | Umbral de lectura (por defecto 0.5) |

### 2.1 Pipeline

```text
Imagen ─► 1. YOLO26 (detección) ─► 2. Recorte ─► 3. EasyOCR (lectura) ─► 4. Validación ─► Respuesta
```

1. **Detección (`app/ml/dl/yolo/detector.py`).** YOLO26 nano (`yolo26n.pt`) analiza la imagen
   completa en una sola pasada y devuelve cajas con su confianza. Se usa la caja con mayor
   confianza (`get_best_detection`). Parámetros: confianza 0.25, IoU 0.45, máximo 10 cajas,
   CPU.
2. **Recorte (`app/utils/image_utils.crop_image`).** Se corta la región de esa caja.
3. **Lectura (`app/ml/dl/ocr/reader.py`).** EasyOCR (idiomas `en` y `es`) lee el texto del
   recorte. Se descartan textos que no tengan la longitud de una placa (6-7 caracteres) y se
   elige el de mayor confianza. Si no hay lectura válida, se reintenta con un umbral 0.1 más
   bajo (nunca menos de 0.2).
4. **Validación (`app/utils/validators.py`).** Se limpia el texto, se normaliza al formato
   `ABC-123` y se calcula el estado final.

El orquestador es `app/services/plate_recognition_service.py`. YOLO y EasyOCR se cargan
**una sola vez** (al arrancar, con `PRELOAD_MODELS=true`) y los comparte la visión aérea.
La inferencia se ejecuta en un hilo aparte (`run_in_threadpool`) para no bloquear el resto
de peticiones.

### 2.2 Respuesta

| Campo | Significado |
|---|---|
| `status` | `SUCCESS`, `NO_PLATE_DETECTED`, `OCR_FAILED`, `LOW_CONFIDENCE`, `INVALID_FORMAT` o `ERROR` |
| `plate_number` | Placa normalizada (`ABC-123`) o `null` |
| `detection.confidence` | Confianza de YOLO en la caja |
| `ocr.text`, `ocr.confidence` | Texto leído y su confianza: **esta** es la confianza que usa parking-service |
| `processing_time_ms` | Tiempo total de inferencia |

## 3. Escenario B: ocupación por visión aérea

Endpoint: `POST /api/v1/parking-vision` (imagen + polígonos de cada espacio).

1. YOLO26 detecta vehículos en la imagen aérea, filtrando las clases COCO de vehículo:
   `car` (2), `motorcycle` (3), `bus` (5) y `truck` (7).
2. Para cada espacio (`app/ml/dl/parking/occupancy_detector.py`): si el **centro** de un
   vehículo cae dentro del polígono, el espacio está ocupado; si no, libre.
3. Los polígonos se dibujan una sola vez con la herramienta local
   `ai/parking-configuration/scripts/configure_polygons.py` (no se despliega) y se validan con
   `POST /api/v1/parking-vision/validate-polygons`.

## 4. Arquitecturas utilizadas

| Modelo | Tipo | Qué aprende | Uso aquí |
|---|---|---|---|
| **YOLO26** (Ultralytics) | Red neuronal convolucional (CNN), detector de una etapa | A predecir en una sola pasada las cajas y la clase de los objetos | Detección de vehículos y de la región a leer |
| **EasyOCR 1.7.2** | Detector de texto CRAFT (CNN) + reconocedor CRNN (CNN + red recurrente LSTM bidireccional + decodificación CTC) | A localizar regiones de texto y transcribir la secuencia de caracteres | Lectura de la placa |

Marco: **PyTorch** (ambos modelos se ejecutan sobre PyTorch, en CPU).

## 5. Registro mínimo en MongoDB

Colección `plate_recognitions` de la base `smart_parking_ia`
(`app/database/recognition_repository.py`):

```javascript
{ captureId, detectedPlate, confidence, processingTimeMs, status, createdAt }
```

- **Sin** cajas, texto crudo del OCR ni imágenes: solo lo necesario para auditoría y métricas.
- `captureId` único: si iot-service reintenta la misma captura, se actualiza el mismo documento.
- Índice TTL sobre `createdAt`: MongoDB borra cada registro a los `MONGODB_RETENTION_DAYS`
  días (90 por defecto), porque una placa es un dato personal.
- Se guarda **después** de responder (tarea en segundo plano) y es opcional: si MongoDB no
  responde, el reconocimiento funciona igual y `GET /api/health` indica `"mongodb": "unavailable"`.
- Si la lectura terminó en una estadía o fue corregida por un operador lo sabe
  `parking-service`, no `ia-service`.

## 6. Limitaciones conocidas

1. **El modelo de detección no tiene una clase "placa".** `yolo26n.pt` es el modelo
   preentrenado en COCO (80 clases: persona, auto, bus, etc.). En el escenario A se toma la
   caja de mayor confianza **de cualquier clase**, que en la práctica suele ser el vehículo
   completo, y EasyOCR busca el texto dentro de ese recorte. Funciona con fotos frontales y
   cercanas, pero baja la precisión con fondo cargado o varios objetos.
2. **Placas amarillas peruanas.** El preentrenamiento no incluye placas peruanas; el contraste
   y la tipografía afectan la lectura. Se deja como trabajo futuro.
3. **CPU.** Sin GPU, cada reconocimiento tarda unos segundos. Por eso iot-service espera hasta
   15 s y, si se excede, la cámara consulta el estado de la captura.
4. **Validación de formato permisiva.** ia-service acepta textos alfanuméricos de 6-7
   caracteres; la validación estricta (`ABC-123` o `A1B-234`) la hace `parking-service`.

## 7. Trabajo futuro (en `ai/`, no en ia-service)

- Ajuste fino (*fine-tuning*) de YOLO26 con un dataset propio de placas peruanas etiquetadas
  (clase única `license_plate`), y reemplazo del archivo de pesos en `YOLO_MODEL_PATH` sin
  cambiar el código del servicio.
- Preprocesamiento del recorte (escala de grises, contraste) antes del OCR.
- Medir precisión, exhaustividad y tiempo por imagen sobre un conjunto de prueba de Huaraz.

## 8. Cómo ejecutar y probar

```bash
cd backend/services/ia-service
python3.12 -m venv .venv
.venv/bin/pip install -r requirements-dev.txt
.venv/bin/python -m app.main            # puerto 8084, documentación en /api/docs
.venv/bin/python -m pytest              # no carga los modelos ni usa MongoDB
```

Las pruebas reemplazan el servicio de reconocimiento y el repositorio con
`app.dependency_overrides`, así que se ejecutan en segundos y sin descargar modelos.
