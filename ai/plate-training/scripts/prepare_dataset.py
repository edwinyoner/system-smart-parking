"""
Paso 1: revisar las etiquetas y repartir el dataset en train / val / test.

Entrada: una carpeta de fotos y otra de etiquetas YOLO (lo que exportan Label Studio, CVAT o
Roboflow en formato "YOLO"), con los mismos nombres de archivo.

Uso:
    python prepare_dataset.py --images ../raw/images --labels ../raw/labels \\
        --output ../datasets/peru-license-plates [--split 0.7 0.2 0.1] [--seed 42] \\
        [--group-regex "^(\\w+?)_"] [--allow-unlabeled]

Copia los archivos (no los mueve) y escribe data.yaml. Si hay etiquetas con errores, no copia
nada y lista cada error.
"""

from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path

import dataset_tools as dt


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description="Preparar el dataset de placas")
    p.add_argument("--images", required=True, type=Path)
    p.add_argument("--labels", required=True, type=Path)
    p.add_argument("--output", required=True, type=Path)
    p.add_argument("--split", nargs=3, type=float, default=[0.7, 0.2, 0.1], metavar=("TRAIN", "VAL", "TEST"))
    p.add_argument("--seed", type=int, default=42)
    p.add_argument("--group-regex", help="Regex sobre el nombre: el primer grupo es el vehículo o la sesión")
    p.add_argument("--allow-unlabeled", action="store_true",
                   help="Fotos sin .txt cuentan como negativos (sin placa) en vez de error")
    p.add_argument("--force", action="store_true", help="Borrar --output si ya existe")
    args = p.parse_args(argv)

    if not args.images.is_dir() or not args.labels.is_dir():
        print("ERROR   --images y --labels deben ser carpetas")
        return 1
    samples = dt.find_samples(args.images, args.labels, args.group_regex)
    if not samples:
        print("ERROR   No hay imágenes")
        return 1

    errors, boxes_total, negatives = [], 0, 0
    for s in samples:
        if s.label is None:
            if args.allow_unlabeled:
                negatives += 1
            else:
                errors.append(f"{s.image.name}: no tiene etiquetas (usa --allow-unlabeled si no tiene placa)")
            continue
        boxes, errs = dt.parse_label_file(s.label.read_text(encoding="utf-8"))
        errors += [f"{s.label.name}: {e}" for e in errs]
        boxes_total += len(boxes)
        negatives += 0 if boxes else 1
    if errors:
        for e in errors[:50]:
            print(f"ERROR   {e}")
        if len(errors) > 50:
            print(f"... y {len(errors) - 50} errores más")
        print("No se copió nada: corrige las etiquetas y vuelve a ejecutar")
        return 1

    try:
        parts = dt.split_groups(samples, tuple(args.split), args.seed)
    except ValueError as e:
        print(f"ERROR   {e}")
        return 1
    if not parts["train"] or not parts["val"]:
        print("ERROR   Muy pocas fotos (o grupos) para tener train y val")
        return 1

    if args.output.exists():
        if not args.force:
            print(f"ERROR   {args.output} ya existe (usa --force para reemplazarlo)")
            return 1
        shutil.rmtree(args.output)
    for split, items in parts.items():
        img_dir, lbl_dir = args.output / "images" / split, args.output / "labels" / split
        img_dir.mkdir(parents=True, exist_ok=True)
        lbl_dir.mkdir(parents=True, exist_ok=True)
        for s in items:
            # Nombre plano: subcarpetas distintas podrían repetir nombres
            name = "__".join(s.image.relative_to(args.images).with_suffix("").parts)
            shutil.copy2(s.image, img_dir / f"{name}{s.image.suffix.lower()}")
            target = lbl_dir / f"{name}.txt"
            if s.label:
                shutil.copy2(s.label, target)
            else:
                target.write_text("", encoding="utf-8")
    (args.output / "data.yaml").write_text(dt.data_yaml(args.output), encoding="utf-8")

    groups = len({s.group for s in samples})
    print(f"Fotos: {len(samples)} ({groups} grupos) | placas etiquetadas: {boxes_total} | sin placa: {negatives}")
    for split in dt.SPLITS:
        print(f"  {split:5}: {len(parts[split])} fotos")
    if not parts["test"]:
        print("AVISO   test quedó vacío: evaluate.py tendrá que usar --split val")
    print(f"Listo: {args.output / 'data.yaml'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
