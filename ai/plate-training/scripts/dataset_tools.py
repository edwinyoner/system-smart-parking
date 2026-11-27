"""
Funciones comunes para el dataset de placas peruanas (formato YOLO).

Cada imagen tiene un .txt con el mismo nombre; una línea por placa:
    0 x_centro y_centro ancho alto
con valores normalizados de 0 a 1. Una sola clase: 0 = license_plate.
Un .txt vacío = imagen sin placas (negativo: ayuda a no ver placas donde no hay).
"""

from __future__ import annotations

import random
import re
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path

CLASS_NAMES = ["license_plate"]
IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}
SPLITS = ("train", "val", "test")


@dataclass(frozen=True)
class Sample:
    image: Path
    label: Path | None  # None = sin archivo de etiquetas
    group: str


def parse_label_file(text: str) -> tuple[list[tuple[int, float, float, float, float]], list[str]]:
    """Devuelve (cajas, errores). Cada error dice la línea y el motivo."""
    boxes, errors = [], []
    for n, line in enumerate(text.splitlines(), start=1):
        line = line.strip()
        if not line:
            continue
        parts = line.split()
        if len(parts) != 5:
            errors.append(f"línea {n}: se esperaban 5 valores (clase x y ancho alto), hay {len(parts)}")
            continue
        try:
            cls = int(parts[0])
            x, y, w, h = (float(v) for v in parts[1:])
        except ValueError:
            errors.append(f"línea {n}: valores no numéricos")
            continue
        if cls < 0 or cls >= len(CLASS_NAMES):
            errors.append(f"línea {n}: clase {cls} no existe (solo 0 = license_plate)")
            continue
        if not all(0.0 <= v <= 1.0 for v in (x, y, w, h)) or w <= 0 or h <= 0:
            errors.append(f"línea {n}: coordenadas fuera de 0-1 o tamaño cero")
            continue
        if x - w / 2 < -0.01 or x + w / 2 > 1.01 or y - h / 2 < -0.01 or y + h / 2 > 1.01:
            errors.append(f"línea {n}: la caja se sale de la imagen")
            continue
        boxes.append((cls, x, y, w, h))
    return boxes, errors


def group_of(image: Path, group_regex: str | None) -> str:
    """
    Grupo de una foto: las del mismo grupo van juntas al mismo conjunto (train, val o test).
    Evita que fotos casi iguales del mismo vehículo estén en train y test a la vez, lo que
    daría métricas falsamente altas. Sin regex, cada foto es su propio grupo.
    """
    if not group_regex:
        return image.stem
    m = re.search(group_regex, image.stem)
    if not m:
        return image.stem
    return m.group(1) if m.groups() else m.group(0)


def find_samples(images_dir: Path, labels_dir: Path, group_regex: str | None = None) -> list[Sample]:
    samples = []
    for image in sorted(p for p in images_dir.rglob("*") if p.suffix.lower() in IMAGE_EXTENSIONS):
        rel = image.relative_to(images_dir).with_suffix(".txt")
        label = labels_dir / rel
        samples.append(Sample(image, label if label.exists() else None, group_of(image, group_regex)))
    return samples


def split_groups(samples: list[Sample], ratios: tuple[float, float, float], seed: int) -> dict[str, list[Sample]]:
    """
    Reparte por GRUPOS (no por foto) con una semilla fija: el mismo dataset da siempre el mismo
    reparto, y así se pueden comparar entrenamientos.
    """
    if len(ratios) != 3 or any(r < 0 for r in ratios) or abs(sum(ratios) - 1.0) > 1e-6:
        raise ValueError("Las proporciones deben ser 3 valores no negativos que sumen 1")
    by_group: dict[str, list[Sample]] = defaultdict(list)
    for s in samples:
        by_group[s.group].append(s)
    groups = sorted(by_group)
    random.Random(seed).shuffle(groups)

    total = len(samples)
    targets = {k: r * total for k, r in zip(SPLITS, ratios)}
    result: dict[str, list[Sample]] = {k: [] for k in SPLITS}
    # Cada grupo va al conjunto al que más le falta para su proporción (así, con pocos grupos,
    # val y test no se quedan vacíos). Empate: el orden train, val, test.
    for g in groups:
        target = max(SPLITS, key=lambda k: (targets[k] - len(result[k]), -SPLITS.index(k)))
        result[target].extend(by_group[g])
    return result


def data_yaml(dataset_dir: Path) -> str:
    """data.yaml de Ultralytics con ruta absoluta (funciona desde cualquier carpeta)."""
    names = "\n".join(f"  {i}: {n}" for i, n in enumerate(CLASS_NAMES))
    return (f"# Generado por prepare_dataset.py\npath: {dataset_dir.resolve()}\n"
            f"train: images/train\nval: images/val\ntest: images/test\nnames:\n{names}\n")
