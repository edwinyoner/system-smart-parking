"""
Paso 3: medir el modelo con el conjunto de PRUEBA (fotos que no vio al entrenar).

Uso:
    python evaluate.py --weights ../runs/placas/weights/best.pt \\
        --data ../datasets/peru-license-plates/data.yaml [--split test] [--conf 0.25]

Métricas (las que conviene reportar en la tesis):
- Precisión: de las placas que detectó, cuántas eran placas de verdad.
- Recall (exhaustividad): de las placas reales, cuántas encontró.
- mAP@0.5: precisión media con una coincidencia de caja de al menos 50 %.
- mAP@0.5:0.95: lo mismo, promediando umbrales del 50 % al 95 % (más exigente).

Esto mide solo la DETECCIÓN de la placa; la lectura del texto (EasyOCR) se mide aparte.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description="Evaluar el detector de placas")
    p.add_argument("--weights", required=True, type=Path)
    p.add_argument("--data", required=True, type=Path)
    p.add_argument("--split", choices=["val", "test"], default="test")
    p.add_argument("--conf", type=float, default=0.25, help="Mismo umbral que YOLO_CONFIDENCE_THRESHOLD")
    p.add_argument("--imgsz", type=int, default=640)
    p.add_argument("--device", default="auto")
    args = p.parse_args(argv)

    for f in (args.weights, args.data):
        if not f.exists():
            print(f"ERROR   No existe {f}")
            return 1
    from ultralytics import YOLO

    from train import pick_device

    model = YOLO(str(args.weights))
    run_dir = args.weights.resolve().parent.parent
    # Gráficas (matriz de confusión, curvas P-R) junto al modelo, no en la carpeta actual
    m = model.val(data=str(args.data.resolve()), split=args.split, conf=args.conf, imgsz=args.imgsz,
                  device=pick_device(args.device), plots=True,
                  project=str(run_dir), name=f"eval-{args.split}", exist_ok=True)
    result = {
        "weights": str(args.weights),
        "split": args.split,
        "precision": round(float(m.box.mp), 4),
        "recall": round(float(m.box.mr), 4),
        "mAP50": round(float(m.box.map50), 4),
        "mAP50_95": round(float(m.box.map), 4),
        "inference_ms_per_image": round(float(m.speed.get("inference", 0.0)), 2),
    }
    print(json.dumps(result, indent=2, ensure_ascii=False))
    out = run_dir / f"metrics-{args.split}.json"
    out.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Guardado en {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
