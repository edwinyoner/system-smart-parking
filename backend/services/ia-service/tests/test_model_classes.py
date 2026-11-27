"""
Selección de clases de los modelos YOLO (sin cargar modelos: solo los nombres de clase).

- Placas: con un modelo propio solo se aceptan sus clases de placa; con el general (COCO) no
  se filtra, como antes.
- Ocupación: los vehículos se buscan por nombre; un modelo sin vehículos se rechaza al cargar.
"""
import pytest

from app.ml.dl.parking.occupancy_detector import ParkingOccupancyDetector
from app.ml.dl.yolo.detector import YOLO26Detector

COCO_NAMES = {0: "person", 1: "bicycle", 2: "car", 3: "motorcycle", 5: "bus", 7: "truck", 9: "traffic light"}


def test_plate_model_uses_only_plate_classes():
    assert YOLO26Detector._find_plate_classes({0: "license_plate"}) == [0]
    assert YOLO26Detector._find_plate_classes({0: "Vehicle", 1: "Plate"}) == [1]
    assert YOLO26Detector._find_plate_classes(["car", "License_Plate "]) == [1]


def test_general_model_is_not_filtered():
    assert YOLO26Detector._find_plate_classes(COCO_NAMES) is None


def test_vehicle_classes_found_by_name():
    assert ParkingOccupancyDetector.find_vehicle_classes(COCO_NAMES) == {
        2: "car", 3: "motorcycle", 5: "bus", 7: "truck"}
    # Otro orden de clases: lo que importa es el nombre, no el número
    assert ParkingOccupancyDetector.find_vehicle_classes(["truck", "person", "car"]) == {0: "truck", 2: "car"}


def test_plate_only_model_has_no_vehicles():
    assert ParkingOccupancyDetector.find_vehicle_classes({0: "license_plate"}) == {}


def test_plate_model_path_falls_back_to_general(monkeypatch):
    from app.core.config import settings

    monkeypatch.setattr(settings, "YOLO_PLATE_MODEL_PATH", "")
    d = YOLO26Detector()
    assert d.model_path == settings.YOLO_MODEL_PATH and not d.dedicated

    monkeypatch.setattr(settings, "YOLO_PLATE_MODEL_PATH", "modelos/placas.pt")
    d = YOLO26Detector()
    assert d.model_path == "modelos/placas.pt" and d.dedicated
    assert d.get_model_info()["dedicated_plate_model"] is True
