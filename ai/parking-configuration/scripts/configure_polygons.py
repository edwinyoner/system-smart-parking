"""
Paso 1: dibujar los polígonos de los espacios sobre la foto aérea.

Abre ParkingPtsSelection de Ultralytics (ventana Tkinter). Cada espacio se marca con
4 clics en sus esquinas, siempre en el mismo sentido (por ejemplo, horario empezando arriba a
la izquierda). Esa ventana NO permite nombrar los espacios: los códigos se asignan después con
convert_polygons.py.

Uso:
    python configure_polygons.py
"""

from __future__ import annotations

import logging
import os
from pathlib import Path

logging.basicConfig(level=logging.INFO, format="%(message)s")
logger = logging.getLogger(__name__)

OUTPUT_DIR = Path(__file__).resolve().parent.parent / "output"


def main() -> None:
    try:
        from ultralytics import solutions
    except ImportError:
        raise SystemExit("Falta Ultralytics: pip install -r requirements.txt")
    try:
        import tkinter  # noqa: F401
    except ImportError:
        raise SystemExit("Falta Tkinter. En macOS con Homebrew: brew install python-tk@3.12")

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    # ParkingPtsSelection guarda bounding_boxes.json en la carpeta actual: se trabaja en output/
    os.chdir(OUTPUT_DIR)

    logger.info("CONFIGURACIÓN DE POLÍGONOS")
    logger.info("1. 'Upload Image' y elegir la foto (de ../images/)")
    logger.info("2. 4 clics por espacio, en las esquinas y siempre en el mismo sentido")
    logger.info("3. 'Remove Last Bounding Box' deshace el último espacio")
    logger.info("4. 'Save' guarda %s", OUTPUT_DIR / "bounding_boxes.json")
    logger.info("Consejo: dibuja por filas, de izquierda a derecha, en el orden de los códigos")

    solutions.ParkingPtsSelection()

    result = OUTPUT_DIR / "bounding_boxes.json"
    if result.exists():
        logger.info("Listo: %s", result)
        logger.info("Siguiente: python convert_polygons.py --input %s --prefix A- --output ...", result)
    else:
        logger.info("No se guardó nada (no se pulsó 'Save')")


if __name__ == "__main__":
    main()
