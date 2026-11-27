"""
Paso 3: validar un JSON de polígonos en el formato de ia-service.

Revisa: códigos como en parking-service, al menos 3 puntos numéricos, sin coordenadas negativas,
dentro de la imagen (con --image), área mínima, lados que no se cruzan, superposición entre
espacios (error desde el 10 %, aviso desde el 2 %) y, con --codes-file, que estén todos los
espacios de la zona y ninguno de más.

Uso:
    python validate_polygons.py --json ../output/parking-1-zone-A.json \\
        [--image ../images/parqueo-1.jpg] [--codes-file codigos.txt] [--preview salida.jpg]

Termina con código 0 si no hay errores (los avisos no impiden usarlo).
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import polygon_tools as pt


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Validar polígonos para ia-service")
    parser.add_argument("--json", required=True, type=Path)
    parser.add_argument("--image", type=Path, help="Foto de la cámara: comprueba los límites")
    parser.add_argument("--codes-file", type=Path, help="Códigos que debe tener (los de la zona)")
    parser.add_argument("--preview", type=Path, help="Guardar la foto con los polígonos (requiere --image)")
    parser.add_argument("--min-area", type=float, default=pt.MIN_AREA_PX, help="Área mínima en px²")
    args = parser.parse_args(argv)

    if args.preview and not args.image:
        parser.error("--preview necesita --image")

    try:
        data = pt.load_json(args.json)
        expected = pt.parse_codes(args.codes_file.read_text(encoding="utf-8")) if args.codes_file else None
        size = pt.image_size(args.image) if args.image else None
    except (OSError, ValueError) as e:
        print(f"ERROR   {e}")
        return 1

    polygons, issues = pt.parse_polygons(data)
    issues += pt.validate(polygons, size, expected, args.min_area)

    print(f"Archivo: {args.json}")
    print(f"Espacios: {len(polygons)}")
    for issue in issues:
        print(issue)

    errors = sum(1 for i in issues if i.level == "ERROR")
    warnings = len(issues) - errors
    if args.preview and polygons:
        pt.draw_preview(polygons, args.image, args.preview)
        print(f"Vista previa: {args.preview}")
    if errors:
        print(f"Validación fallida: {errors} error(es), {warnings} aviso(s)")
        return 1
    print(f"Válido ({warnings} aviso(s)). Se puede enviar a ia-service en POST /api/v1/parking-vision")
    return 0


if __name__ == "__main__":
    sys.exit(main())
