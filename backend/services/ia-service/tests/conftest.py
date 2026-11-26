# ia-service/tests/conftest.py

"""
Configuración común de las pruebas.

Las pruebas no cargan YOLO ni EasyOCR ni se conectan a MongoDB: el servicio de
reconocimiento y el repositorio se reemplazan con dependency_overrides de FastAPI.
"""
import os

# Antes de importar la aplicación: sin MongoDB ni precarga de modelos
os.environ["MONGODB_ENABLED"] = "false"
os.environ["PRELOAD_MODELS"] = "false"

import cv2
import numpy as np
import pytest


@pytest.fixture
def jpeg_bytes() -> bytes:
    """Imagen JPEG válida y pequeña (gris) para enviar como archivo"""
    image = np.full((120, 200, 3), 127, dtype=np.uint8)
    ok, buffer = cv2.imencode(".jpg", image)
    assert ok
    return buffer.tobytes()
