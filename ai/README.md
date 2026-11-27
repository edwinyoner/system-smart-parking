# ai - Herramientas de IA fuera de producción

Herramientas, scripts y (más adelante) datasets de inteligencia artificial que **no se
despliegan**. Se ejecutan a mano, en la computadora del desarrollador.

| Carpeta | Qué hace | Lo que corre en producción |
|---|---|---|
| `ai/` | Configurar polígonos, entrenar o ajustar modelos, experimentar | No se despliega |
| [`backend/services/ia-service`](../backend/services/ia-service/README.md) | **Solo inferencia** con los modelos ya entrenados (YOLO26 + EasyOCR) | Sí |

```text
ai/ (local)                                    ia-service (producción)
 ├─ polígonos de cada espacio (JSON) ────────►  POST /api/v1/parking-vision
 └─ pesos ajustados (.pt) (futuro) ──────────►  YOLO_MODEL_PATH, sin cambiar el código
```

## Contenido actual

```text
ai/
├── README.md
├── .gitignore                      fotos, vistas previas y borradores fuera de Git
├── parking-configuration/          polígonos de los espacios (escenario B, parqueo abierto)
│   ├── images/                     fotos aéreas (no se suben a Git)
│   ├── output/                     JSON finales {"A-01": [[x, y], ...]} para ia-service
│   ├── scripts/
│   │   ├── configure_polygons.py   1. dibujar (Ultralytics ParkingPtsSelection)
│   │   ├── convert_polygons.py     2. poner los códigos de parking-service y vista previa
│   │   ├── validate_polygons.py    3. validar (forma, límites, cruces, superposición, códigos)
│   │   ├── polygon_tools.py        funciones comunes
│   │   └── requirements*.txt
│   └── tests/                      pytest
└── plate-training/                 detector de placas peruanas (ajuste fino de YOLO26)
    ├── raw/  datasets/  runs/      fotos, dataset repartido y resultados (no se suben a Git)
    ├── scripts/
    │   ├── prepare_dataset.py      1. revisar etiquetas y repartir train / val / test
    │   ├── train.py                2. entrenar
    │   ├── evaluate.py             3. métricas con el conjunto de prueba
    │   └── dataset_tools.py        funciones comunes
    └── tests/                      pytest
```

Detalle: [`parking-configuration/README.md`](parking-configuration/README.md) y
[`plate-training/README.md`](plate-training/README.md).

## Trabajo futuro

- **Dataset real de placas peruanas** (fotos de las cámaras, etiquetadas): los scripts de
  `plate-training/` ya están listos para entrenar y evaluar con él.
- En ia-service: un modelo de placas aparte del de ocupación (`YOLO_PLATE_MODEL_PATH`) y
  Ultralytics 8.4 (hoy 8.3.50). Ver [`plate-training/README.md`](plate-training/README.md#6-usarlo-en-ia-service-pendiente).
- Guardar los polígonos de cada zona en el sistema.

## Reglas

- Nada de esta carpeta va a un contenedor ni a la nube.
- Los pesos `.pt` pesados no se suben a Git.
- **No subir imágenes con placas o personas reconocibles**: son datos personales. Anonimizar los
  datasets antes de compartirlos.
