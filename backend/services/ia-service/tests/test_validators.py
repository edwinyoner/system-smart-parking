# ia-service/tests/test_validators.py

"""Pruebas de la normalización de placas y del estado final del reconocimiento"""
from app.core.constants import (
    STATUS_INVALID_FORMAT,
    STATUS_LOW_CONFIDENCE,
    STATUS_NO_PLATE_DETECTED,
    STATUS_OCR_FAILED,
    STATUS_SUCCESS,
)
from app.utils.formatters import get_status_and_message
from app.utils.validators import normalize_plate_format, validate_plate_format


def test_normaliza_placa_sin_guion():
    assert normalize_plate_format("ABC123") == "ABC-123"


def test_normaliza_placa_con_espacios_y_minusculas():
    assert normalize_plate_format(" abc 123 ") == "ABC-123"


def test_placa_con_formato_valido():
    assert validate_plate_format("ABC-123")


def test_texto_demasiado_corto_no_se_normaliza():
    assert normalize_plate_format("AB") is None


def test_estado_segun_el_resultado():
    def status(**kw):
        base = dict(has_detection=True, has_ocr=True, ocr_confidence=0.9,
                    has_valid_format=True, min_confidence=0.5)
        base.update(kw)
        return get_status_and_message(**base)[0]

    assert status() == STATUS_SUCCESS
    assert status(has_detection=False) == STATUS_NO_PLATE_DETECTED
    assert status(has_ocr=False) == STATUS_OCR_FAILED
    assert status(ocr_confidence=0.3) == STATUS_LOW_CONFIDENCE
    assert status(has_valid_format=False) == STATUS_INVALID_FORMAT
