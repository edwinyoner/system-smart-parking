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
└── parking-configuration/          polígonos de los espacios (escenario B, parqueo abierto)
    ├── images/                     fotos aéreas (no subirlas a Git)
    ├── output/                     JSON generados
    └── scripts/
        ├── configure_polygons.py   dibuja los polígonos (Ultralytics ParkingPtsSelection)
        ├── validate_polygons.py    revisa el JSON generado
        └── requirements.txt
```

Detalle: [`parking-configuration/README.md`](parking-configuration/README.md).

## Trabajo futuro

Ajuste fino (*fine-tuning*) de YOLO26 para **placas peruanas**. Hoy ia-service usa el modelo
general de COCO, que no tiene una clase "placa" ni se entrenó con placas peruanas (ver
[`DEEP_LEARNING.md`](../backend/services/ia-service/DEEP_LEARNING.md)). Estructura prevista:

```text
ai/
├── datasets/peru-license-plates/   images/ y labels/ (train, val, test) + data.yaml
├── training/                       scripts de entrenamiento y evaluación
└── models/                         pesos resultantes (fuera de Git o con Git LFS)
```

El modelo resultante se copia a ia-service y se apunta con `YOLO_MODEL_PATH`.

## Reglas

- Nada de esta carpeta va a un contenedor ni a la nube.
- Los pesos `.pt` pesados no se suben a Git.
- **No subir imágenes con placas o personas reconocibles**: son datos personales. Anonimizar los
  datasets antes de compartirlos.
