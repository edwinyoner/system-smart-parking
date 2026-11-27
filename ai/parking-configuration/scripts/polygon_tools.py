"""
Funciones comunes para los polígonos de los espacios (escenario B, parqueo abierto).

Dos formatos:
- Ultralytics (lo que guarda ParkingPtsSelection en bounding_boxes.json): una lista
  [{"points": [[x, y], ...]}, ...] en el orden en que se dibujaron, SIN nombre de espacio.
- ia-service (POST /api/v1/parking-vision): un diccionario {"A-01": [[x, y], ...], ...} cuya
  clave es el CÓDIGO del espacio en parking-service. iot-service usa ese código para saber qué
  espacio actualizar, así que debe coincidir exactamente.

Las coordenadas son píxeles de la imagen ORIGINAL de la cámara (Ultralytics ya las reescala).
"""

from __future__ import annotations

import json
import math
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

# Mismo patrón que Space.CODE_PATTERN en parking-service
SPACE_CODE_PATTERN = re.compile(r"^[A-Z0-9][A-Z0-9_-]{0,19}$")

# Superposición entre dos espacios, sobre el área del más pequeño
OVERLAP_ERROR = 0.10
OVERLAP_WARNING = 0.02
MIN_AREA_PX = 100.0

Point = tuple[float, float]
Polygons = dict[str, list[Point]]


@dataclass(frozen=True)
class Issue:
    """Un problema encontrado. level: ERROR (no se puede usar) o WARNING (revisar)."""

    level: str
    space: str | None
    message: str

    def __str__(self) -> str:
        where = f"[{self.space}] " if self.space else ""
        return f"{self.level:7} {where}{self.message}"


# ============================================================================
# LECTURA Y ESCRITURA
# ============================================================================

def _reject_duplicate_keys(pairs):
    seen: dict = {}
    for key, value in pairs:
        if key in seen:
            raise ValueError(f"El código {key!r} aparece dos veces en el JSON")
        seen[key] = value
    return seen


def load_json(path: Path):
    """Lee el JSON rechazando códigos repetidos (json normal se quedaría con el último)."""
    with open(path, encoding="utf-8") as f:
        return json.load(f, object_pairs_hook=_reject_duplicate_keys)


def is_ultralytics_format(data) -> bool:
    return isinstance(data, list) and all(isinstance(b, dict) and "points" in b for b in data)


def boxes_from_ultralytics(data) -> list[list[Point]]:
    """Polígonos de bounding_boxes.json, en el orden en que se dibujaron."""
    if not is_ultralytics_format(data):
        raise ValueError('No es el formato de Ultralytics: se esperaba [{"points": [[x, y], ...]}, ...]')
    return [[(float(p[0]), float(p[1])) for p in box["points"]] for box in data]


def save_polygons(polygons: Polygons, path: Path) -> None:
    """Guarda en el formato de ia-service, con coordenadas enteras."""
    path.parent.mkdir(parents=True, exist_ok=True)
    out = {code: [[round(x), round(y)] for x, y in pts] for code, pts in polygons.items()}
    with open(path, "w", encoding="utf-8") as f:
        json.dump(out, f, indent=2, ensure_ascii=False)
        f.write("\n")


# ============================================================================
# CÓDIGOS Y ORDEN
# ============================================================================

def generate_codes(count: int, prefix: str, start: int = 1, digits: int = 2) -> list[str]:
    """Igual que el lote de espacios del panel: prefijo + número con ceros (A-01, A-02...)."""
    if count < 0 or start < 0 or digits < 1:
        raise ValueError("count y start no pueden ser negativos; digits debe ser al menos 1")
    return [f"{prefix}{str(start + i).zfill(digits)}".upper() for i in range(count)]


def parse_codes(text: str) -> list[str]:
    """Códigos separados por comas, espacios o saltos de línea; '#' inicia un comentario."""
    codes = []
    for line in text.splitlines():
        line = line.split("#", 1)[0]
        codes.extend(c.strip().upper() for c in re.split(r"[,\s]+", line) if c.strip())
    return codes


def centroid(points: list[Point]) -> Point:
    """Centroide del área (si el área es casi cero, el promedio de los vértices)."""
    a = signed_area(points)
    if abs(a) < 1e-9:
        return (sum(p[0] for p in points) / len(points), sum(p[1] for p in points) / len(points))
    cx = cy = 0.0
    for (x1, y1), (x2, y2) in _edges(points):
        cross = x1 * y2 - x2 * y1
        cx += (x1 + x2) * cross
        cy += (y1 + y2) * cross
    return (cx / (6 * a), cy / (6 * a))


def order_by_rows(boxes: list[list[Point]]) -> list[list[Point]]:
    """
    Ordena como se lee: filas de arriba hacia abajo y, en cada fila, de izquierda a derecha.
    Dos espacios están en la misma fila si sus centros difieren menos de media altura típica.
    """
    if not boxes:
        return []
    heights = sorted(max(p[1] for p in b) - min(p[1] for p in b) for b in boxes)
    tolerance = max(heights[len(heights) // 2] / 2, 1.0)
    items = sorted(((centroid(b), b) for b in boxes), key=lambda t: t[0][1])
    rows: list[list] = []
    for c, b in items:
        if rows and abs(c[1] - rows[-1][0]) <= tolerance:
            row = rows[-1]
            row[1].append((c, b))
            row[0] = sum(x[0][1] for x in row[1]) / len(row[1])
        else:
            rows.append([c[1], [(c, b)]])
    return [b for _, row in rows for _, b in sorted(row, key=lambda t: t[0][0])]


def assign_codes(boxes: list[list[Point]], codes: list[str]) -> Polygons:
    if len(codes) != len(boxes):
        raise ValueError(f"Hay {len(boxes)} polígonos y {len(codes)} códigos: deben ser iguales")
    if len(set(codes)) != len(codes):
        repeated = sorted({c for c in codes if codes.count(c) > 1})
        raise ValueError(f"Códigos repetidos: {', '.join(repeated)}")
    return dict(zip(codes, boxes))


# ============================================================================
# GEOMETRÍA
# ============================================================================

def _edges(points: list[Point]) -> Iterable[tuple[Point, Point]]:
    for i in range(len(points)):
        yield points[i], points[(i + 1) % len(points)]


def signed_area(points: list[Point]) -> float:
    return sum(x1 * y2 - x2 * y1 for (x1, y1), (x2, y2) in _edges(points)) / 2


def area(points: list[Point]) -> float:
    return abs(signed_area(points))


def _orientation(a: Point, b: Point, c: Point) -> float:
    return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])


def _segments_cross(p1: Point, p2: Point, q1: Point, q2: Point) -> bool:
    """Cruce propio (los extremos compartidos no cuentan)."""
    d1, d2 = _orientation(q1, q2, p1), _orientation(q1, q2, p2)
    d3, d4 = _orientation(p1, p2, q1), _orientation(p1, p2, q2)
    return d1 * d2 < 0 and d3 * d4 < 0


def is_simple(points: list[Point]) -> bool:
    """False si dos lados no contiguos se cruzan (polígono "en ocho" por clics desordenados)."""
    edges = list(_edges(points))
    n = len(edges)
    for i in range(n):
        for j in range(i + 1, n):
            if j == i + 1 or (i == 0 and j == n - 1):
                continue
            if _segments_cross(*edges[i], *edges[j]):
                return False
    return True


def _bbox(points: list[Point]) -> tuple[float, float, float, float]:
    xs, ys = [p[0] for p in points], [p[1] for p in points]
    return min(xs), min(ys), max(xs), max(ys)


def _pixel_mask(points: list[Point], x1: int, y1: int, x2: int, y2: int):
    """Máscara del polígono dentro del recuadro [x1, x2] x [y1, y2] (bordes incluidos)."""
    import cv2
    import numpy as np

    mask = np.zeros((y2 - y1 + 1, x2 - x1 + 1), dtype=np.uint8)
    shifted = np.round(np.array(points) - np.array([x1, y1], dtype=np.float64)).astype(np.int32)
    cv2.fillPoly(mask, [shifted], 1)
    return mask


def _pixel_area(points: list[Point]) -> int:
    import numpy as np

    x1, y1, x2, y2 = _bbox(points)
    return int(np.count_nonzero(_pixel_mask(points, math.floor(x1), math.floor(y1), math.ceil(x2), math.ceil(y2))))


def overlap_ratio(a: list[Point], b: list[Point]) -> float:
    """
    Área común / área del polígono más pequeño (0 a 1). Se cuentan píxeles en ambos lados de
    la división (así el borde que fillPoly incluye no infla el resultado) y solo dentro del
    recuadro donde ambos se tocan: sirve con fotos grandes y muchos espacios.
    """
    import numpy as np

    ax1, ay1, ax2, ay2 = _bbox(a)
    bx1, by1, bx2, by2 = _bbox(b)
    x1, y1 = math.floor(max(ax1, bx1)), math.floor(max(ay1, by1))
    x2, y2 = math.ceil(min(ax2, bx2)), math.ceil(min(ay2, by2))
    if x2 <= x1 or y2 <= y1:
        return 0.0
    inter = int(np.count_nonzero(_pixel_mask(a, x1, y1, x2, y2) & _pixel_mask(b, x1, y1, x2, y2)))
    smaller = min(_pixel_area(a), _pixel_area(b))
    return 0.0 if smaller == 0 else min(inter / smaller, 1.0)


# ============================================================================
# VALIDACIÓN
# ============================================================================

def parse_polygons(data) -> tuple[Polygons, list[Issue]]:
    """Convierte el JSON al formato interno. Los errores de forma impiden seguir con ese espacio."""
    issues: list[Issue] = []
    if is_ultralytics_format(data):
        return {}, [Issue("ERROR", None, "Es el formato de Ultralytics (sin códigos de espacio): "
                                          "conviértelo primero con convert_polygons.py")]
    if not isinstance(data, dict):
        return {}, [Issue("ERROR", None, 'Se esperaba un objeto {"A-01": [[x, y], ...], ...}')]
    if not data:
        return {}, [Issue("ERROR", None, "No hay ningún espacio")]

    polygons: Polygons = {}
    for code, raw in data.items():
        if not isinstance(code, str) or not SPACE_CODE_PATTERN.match(code):
            issues.append(Issue("ERROR", str(code), "Código no válido: 1 a 20 caracteres, mayúsculas, "
                                                    "números, '-' o '_' (como en parking-service)"))
            continue
        if not isinstance(raw, list) or len(raw) < 3:
            issues.append(Issue("ERROR", code, "Debe tener al menos 3 puntos"))
            continue
        points: list[Point] = []
        for i, p in enumerate(raw):
            ok = (isinstance(p, (list, tuple)) and len(p) == 2
                  and all(isinstance(v, (int, float)) and not isinstance(v, bool) and math.isfinite(v) for v in p))
            if not ok:
                issues.append(Issue("ERROR", code, f"El punto {i + 1} debe ser [x, y] con números"))
                points = []
                break
            points.append((float(p[0]), float(p[1])))
        if points:
            polygons[code] = points
    return polygons, issues


def validate(polygons: Polygons, image_size: tuple[int, int] | None = None,
             expected_codes: list[str] | None = None, min_area: float = MIN_AREA_PX) -> list[Issue]:
    """Revisa cada polígono y los pares que se superponen."""
    issues: list[Issue] = []
    for code, pts in polygons.items():
        if any(x < 0 or y < 0 for x, y in pts):
            issues.append(Issue("ERROR", code, "Tiene coordenadas negativas"))
        if image_size:
            w, h = image_size
            if any(x > w or y > h for x, y in pts):
                issues.append(Issue("ERROR", code, f"Se sale de la imagen ({w}x{h})"))
        a = area(pts)
        if a < min_area:
            issues.append(Issue("ERROR", code, f"Área de {a:.0f} px²: demasiado pequeña (mín. {min_area:.0f})"))
        if not is_simple(pts):
            issues.append(Issue("ERROR", code, "Sus lados se cruzan: las esquinas no se marcaron en orden"))

    codes = list(polygons)
    for i in range(len(codes)):
        for j in range(i + 1, len(codes)):
            r = overlap_ratio(polygons[codes[i]], polygons[codes[j]])
            if r >= OVERLAP_ERROR:
                issues.append(Issue("ERROR", codes[i], f"Se superpone {r:.0%} con {codes[j]}"))
            elif r >= OVERLAP_WARNING:
                issues.append(Issue("WARNING", codes[i], f"Se superpone {r:.0%} con {codes[j]}"))

    if expected_codes is not None:
        missing = [c for c in expected_codes if c not in polygons]
        extra = [c for c in polygons if c not in set(expected_codes)]
        if missing:
            issues.append(Issue("ERROR", None, f"Faltan espacios: {', '.join(missing)}"))
        if extra:
            issues.append(Issue("ERROR", None, f"Espacios que no existen en la zona: {', '.join(extra)}"))
    return issues


def image_size(path: Path) -> tuple[int, int]:
    from PIL import Image

    with Image.open(path) as img:
        return img.size


def draw_preview(polygons: Polygons, image_path: Path, out_path: Path) -> None:
    """Dibuja cada polígono con su código sobre la foto, para revisarlo a simple vista."""
    import cv2
    import numpy as np

    img = cv2.imread(str(image_path))
    if img is None:
        raise ValueError(f"No se pudo leer la imagen {image_path}")
    overlay = img.copy()
    thickness = max(2, round(max(img.shape[:2]) / 600))
    font_scale = max(0.5, max(img.shape[:2]) / 1600)
    for code, pts in polygons.items():
        arr = np.round(np.array(pts)).astype(np.int32)
        cv2.fillPoly(overlay, [arr], (60, 180, 75))
        cv2.polylines(img, [arr], True, (60, 180, 75), thickness)
    img = cv2.addWeighted(overlay, 0.25, img, 0.75, 0)
    for code, pts in polygons.items():
        cx, cy = centroid(pts)
        (tw, th), _ = cv2.getTextSize(code, cv2.FONT_HERSHEY_SIMPLEX, font_scale, thickness)
        org = (int(cx - tw / 2), int(cy + th / 2))
        cv2.putText(img, code, org, cv2.FONT_HERSHEY_SIMPLEX, font_scale, (0, 0, 0), thickness + 3, cv2.LINE_AA)
        cv2.putText(img, code, org, cv2.FONT_HERSHEY_SIMPLEX, font_scale, (255, 255, 255), thickness, cv2.LINE_AA)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    if not cv2.imwrite(str(out_path), img):
        raise ValueError(f"No se pudo guardar {out_path}")
