"""
Paso 2: poner a cada polígono el CÓDIGO de su espacio en parking-service.

Convierte bounding_boxes.json (Ultralytics, lista sin nombres) al formato de ia-service
{"A-01": [[x, y], ...]}, valida el resultado y, con --image, dibuja una vista previa.

Los códigos se dan de una de estas formas (en el orden de --order):
  --prefix A- --start 1 --digits 2     A-01, A-02... (igual que el lote de espacios del panel)
  --codes A-01,A-02,B-01               lista explícita
  --codes-file codigos.txt             un código por línea (o separados por comas)

Orden de los polígonos:
  --order rows    (por defecto) filas de arriba hacia abajo, cada fila de izquierda a derecha
  --order clicks  el orden en que se dibujaron

Ejemplo:
    python convert_polygons.py --input ../output/bounding_boxes.json --prefix A- \\
        --image ../images/parqueo-1.jpg --output ../output/parking-1-zone-A.json
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import polygon_tools as pt


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Asignar códigos de espacio a los polígonos")
    parser.add_argument("--input", required=True, type=Path, help="bounding_boxes.json de Ultralytics")
    parser.add_argument("--output", required=True, type=Path, help="JSON para ia-service")
    codes = parser.add_mutually_exclusive_group(required=True)
    codes.add_argument("--prefix", help="Prefijo de los códigos generados (ej. A-)")
    codes.add_argument("--codes", help="Códigos separados por comas")
    codes.add_argument("--codes-file", type=Path, help="Archivo con los códigos")
    parser.add_argument("--start", type=int, default=1, help="Primer número (con --prefix)")
    parser.add_argument("--digits", type=int, default=2, help="Dígitos del número (con --prefix)")
    parser.add_argument("--order", choices=["rows", "clicks"], default="rows")
    parser.add_argument("--image", type=Path, help="Foto usada: valida los límites y genera la vista previa")
    parser.add_argument("--force", action="store_true", help="Sobrescribir --output si ya existe")
    args = parser.parse_args(argv)

    try:
        boxes = pt.boxes_from_ultralytics(pt.load_json(args.input))
        if not boxes:
            raise ValueError("bounding_boxes.json no tiene polígonos")
        if args.order == "rows":
            boxes = pt.order_by_rows(boxes)

        if args.prefix is not None:
            code_list = pt.generate_codes(len(boxes), args.prefix, args.start, args.digits)
        elif args.codes is not None:
            code_list = pt.parse_codes(args.codes)
        else:
            code_list = pt.parse_codes(args.codes_file.read_text(encoding="utf-8"))
        polygons = pt.assign_codes(boxes, code_list)
    except (OSError, ValueError) as e:
        print(f"ERROR   {e}")
        return 1

    size = pt.image_size(args.image) if args.image else None
    issues = pt.validate(polygons, size)
    for issue in issues:
        print(issue)
    if any(i.level == "ERROR" for i in issues):
        print("No se guardó: corrige los errores (vuelve a dibujar o revisa los códigos)")
        return 1

    if args.output.exists() and not args.force:
        print(f"ERROR   {args.output} ya existe (usa --force para sobrescribir)")
        return 1
    pt.save_polygons(polygons, args.output)
    print(f"Guardado {args.output} con {len(polygons)} espacios: {', '.join(polygons)}")

    if args.image:
        preview = args.output.with_name(args.output.stem + "-preview.jpg")
        pt.draw_preview(polygons, args.image, preview)
        print(f"Vista previa: {preview} (revisa que cada código esté en su espacio)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
