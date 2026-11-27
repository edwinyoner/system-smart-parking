"""
Paso 2: ajuste fino (fine-tuning) de YOLO26 para detectar placas peruanas.

Parte de los pesos generales (yolo26n.pt, entrenados con COCO) y los ajusta con el dataset de
placas: aprovecha lo que el modelo ya sabe de formas y bordes, y necesita muchas menos fotos que
entrenar desde cero.

Uso:
    python train.py --data ../datasets/peru-license-plates/data.yaml [--model yolo26n.pt] \\
        [--epochs 100] [--imgsz 640] [--batch 16] [--device auto] [--name placas-v1]

Resultado: ../runs/<name>/weights/best.pt (el mejor según val) y gráficas de entrenamiento.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

RUNS_DIR = Path(__file__).resolve().parent.parent / "runs"


def pick_device(requested: str) -> str:
    """auto: GPU NVIDIA (cuda), Apple Silicon (mps) o CPU, en ese orden."""
    if requested != "auto":
        return requested
    import torch

    if torch.cuda.is_available():
        return "0"
    if getattr(torch.backends, "mps", None) and torch.backends.mps.is_available():
        return "mps"
    return "cpu"


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description="Entrenar el detector de placas")
    p.add_argument("--data", required=True, type=Path, help="data.yaml de prepare_dataset.py")
    p.add_argument("--model", default="yolo26n.pt", help="Pesos de partida (se descargan si no están)")
    p.add_argument("--epochs", type=int, default=100)
    p.add_argument("--imgsz", type=int, default=640)
    p.add_argument("--batch", type=int, default=16, help="-1 = automático según la memoria")
    p.add_argument("--patience", type=int, default=20, help="Detener si val no mejora en N épocas")
    p.add_argument("--device", default="auto", help="auto, cpu, mps, 0 (primera GPU)...")
    p.add_argument("--name", default="placas", help="Carpeta del resultado dentro de --project")
    p.add_argument("--project", type=Path, default=RUNS_DIR, help="Carpeta de resultados (def. ai/plate-training/runs)")
    p.add_argument("--seed", type=int, default=42)
    args = p.parse_args(argv)

    if not args.data.exists():
        print(f"ERROR   No existe {args.data}: ejecuta antes prepare_dataset.py")
        return 1
    from ultralytics import YOLO

    device = pick_device(args.device)
    print(f"Entrenando {args.model} en {device} con {args.data}")
    model = YOLO(args.model)
    model.train(
        data=str(args.data.resolve()),
        epochs=args.epochs,
        imgsz=args.imgsz,
        batch=args.batch,
        patience=args.patience,
        device=device,
        project=str(args.project.resolve()),
        name=args.name,
        seed=args.seed,
        deterministic=True,
        # Las placas se leen de izquierda a derecha: voltear la imagen enseñaría placas al revés
        fliplr=0.0,
    )
    best = Path(model.trainer.best)
    print(f"Mejor modelo: {best}")
    print(f"Siguiente: python evaluate.py --weights {best} --data {args.data}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
