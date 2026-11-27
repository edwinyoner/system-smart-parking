# Entrenamiento del detector de placas peruanas

Scripts para el **ajuste fino** (*fine-tuning*) de YOLO26 con fotos de placas peruanas. Se
ejecutan en la computadora del desarrollador; ia-service solo usa el modelo ya entrenado.

> **Estado:** los scripts están listos y probados con un dataset sintético. Falta el dataset
> real: fotos de las cámaras etiquetadas a mano.

## Por qué hace falta

ia-service usa hoy `yolo26n.pt`, el modelo general entrenado con COCO. Tiene 80 clases (persona,
auto, bus...) y **ninguna es "placa"**: en el escenario A recorta el objeto de mayor confianza,
que suele ser el vehículo entero, y EasyOCR tiene que buscar la placa en él. Un detector entrenado
con placas peruanas recorta solo la placa y mejora la lectura (ver
[`DEEP_LEARNING.md`](../../backend/services/ia-service/DEEP_LEARNING.md)).

## 0. Preparar el entorno

```bash
cd ai/plate-training/scripts
python3.12 -m venv .venv && .venv/bin/pip install -r requirements.txt
```

Requiere **Ultralytics 8.4 o posterior**: con la 8.3 el entrenamiento de YOLO26 falla al
reconstruir el modelo. En una Mac con Apple Silicon entrena con la GPU (`mps`); sin GPU funciona
en CPU, pero mucho más lento (conviene Google Colab con GPU para entrenamientos largos).

## 1. Juntar las fotos

- Fotos **de las mismas cámaras** que se instalarán (ESP32-S3 con OV2640, misma resolución),
  o lo más parecidas posible. Un modelo rinde peor con fotos distintas a las que vio al entrenar.
- Variedad: día, noche, lluvia, sol de frente, placas sucias o inclinadas, autos y motos,
  placas de **fondo amarillo** (servicio público) y blanco.
- Varias fotos del mismo vehículo valen menos que fotos de vehículos distintos.
- Algunas fotos **sin placa** (calle vacía, la barrera) ayudan a no ver placas donde no hay.
- Orientación: unas 300 a 500 placas para empezar; más de 1000 para resultados sólidos.

**Datos personales:** las placas y las caras lo son. Las fotos se quedan en `raw/` y
`datasets/`, que **no se suben a Git**. Para compartir el dataset, difumina las caras y pide
permiso a quien corresponda (por ejemplo, a la administración del parqueo).

## 2. Etiquetar

Con [Label Studio](https://labelstud.io/), [CVAT](https://www.cvat.ai/) o Roboflow: dibujar un
rectángulo ajustado a cada placa, una sola clase **`license_plate`**, y exportar en formato
**YOLO**. Queda un `.txt` por foto con líneas `0 x_centro y_centro ancho alto` (de 0 a 1):

```text
raw/
├── images/   ABC123_01.jpg, ABC123_02.jpg, ...
└── labels/   ABC123_01.txt, ABC123_02.txt, ...
```

Si el nombre empieza con la placa o la sesión (`ABC123_01.jpg`), usa `--group-regex` en el
paso 3 para que las fotos del mismo vehículo no queden repartidas entre entrenamiento y prueba
(eso daría métricas falsamente altas).

## 3. Preparar el dataset

```bash
.venv/bin/python prepare_dataset.py --images ../raw/images --labels ../raw/labels \
  --output ../datasets/peru-license-plates --group-regex '^(\w+?)_' [--allow-unlabeled]
```

Revisa cada etiqueta (clase, 5 valores, coordenadas de 0 a 1, cajas dentro de la imagen); si hay
errores, los lista y no copia nada. Reparte 70 / 20 / 10 % en train / val / test **por grupos**
y con semilla fija (`--seed`, el mismo reparto siempre), y escribe `data.yaml`.

## 4. Entrenar

```bash
.venv/bin/python train.py --data ../datasets/peru-license-plates/data.yaml --name placas-v1
```

Parte de `yolo26n.pt` (se descarga solo), 100 épocas como máximo; se detiene antes si no mejora
en 20 (`--patience`). Desactiva el volteo horizontal de las imágenes, porque una placa al revés
no existe. Resultado en `runs/placas-v1/`: `weights/best.pt` y gráficas del entrenamiento.

## 5. Evaluar

```bash
.venv/bin/python evaluate.py --weights ../runs/placas-v1/weights/best.pt \
  --data ../datasets/peru-license-plates/data.yaml
```

Mide con el conjunto de **prueba** (fotos que el modelo no vio) y guarda
`runs/placas-v1/metrics-test.json` y las gráficas en `runs/placas-v1/eval-test/`:

| Métrica | Qué significa |
|---|---|
| Precisión | De lo que detectó como placa, cuánto era placa |
| Recall | De las placas reales, cuántas encontró |
| mAP@0.5 | Precisión media con coincidencia de caja de al menos 50 % |
| mAP@0.5:0.95 | Igual, promediando umbrales del 50 al 95 % (más exigente) |
| ms por imagen | Tiempo de inferencia en el equipo usado |

Sirven para los resultados de la tesis (comparar el modelo general con el ajustado). Solo miden
la **detección**; la lectura del texto (EasyOCR) se mide aparte.

## 6. Usarlo en ia-service (pendiente)

Antes de copiar `best.pt` a ia-service hay dos ajustes allí:

1. **Un modelo para cada cosa.** Hoy las placas y la ocupación aérea comparten `YOLO_MODEL_PATH`.
   El modelo de placas no detecta vehículos, así que ia-service necesita una variable aparte
   (por ejemplo `YOLO_PLATE_MODEL_PATH`) y seguir usando COCO para la ocupación.
2. **Actualizar Ultralytics** (`ultralytics==8.3.50` en `requirements.txt`, anterior a YOLO26) a
   la misma versión 8.4 con que se entrenó. En la prueba, la 8.3.50 cargó y usó el modelo
   entrenado, pero conviene no depender de eso.

## Archivos

| Archivo | Qué hace |
|---|---|
| `scripts/prepare_dataset.py` | Revisa etiquetas, reparte por grupos y escribe `data.yaml` |
| `scripts/train.py` | Ajuste fino de YOLO26 (elige `cuda`, `mps` o `cpu`) |
| `scripts/evaluate.py` | Métricas con el conjunto de prueba |
| `scripts/dataset_tools.py` | Funciones comunes (etiquetas, grupos, reparto) |
| `tests/` | Pruebas (no necesitan Ultralytics ni PyTorch) |

```bash
cd ai/plate-training
python3.12 -m venv .venv-test && .venv-test/bin/pip install -r scripts/requirements-dev.txt
.venv-test/bin/python -m pytest -q tests
```
