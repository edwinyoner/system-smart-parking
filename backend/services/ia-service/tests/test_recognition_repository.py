# ia-service/tests/test_recognition_repository.py

"""Pruebas del registro mínimo en MongoDB con una colección falsa"""
from datetime import datetime, timezone

import pytest

from app.database.mongodb import PLATE_RECOGNITIONS
from app.database.recognition_repository import RecognitionRepository, build_document
from app.schemas.base import BoundingBox
from app.schemas.recognition import DetectionResult, OCRResult, RecognitionResponse

NOW = datetime(2026, 11, 26, 10, 30, tzinfo=timezone.utc)


class FakeCollection:
    def __init__(self, error=None):
        self.updates, self.inserts, self.error = [], [], error

    async def update_one(self, filter, update, upsert=False):
        if self.error:
            raise self.error
        self.updates.append((filter, update, upsert))

    async def insert_one(self, document):
        if self.error:
            raise self.error
        self.inserts.append(document)


class FakeDatabase(dict):
    pass


def response(plate="ABC-123", ocr_conf=0.9) -> RecognitionResponse:
    return RecognitionResponse(
        status="SUCCESS", message="ok", plate_number=plate,
        detection=DetectionResult(bbox=BoundingBox(x=1, y=1, width=5, height=5), confidence=0.95),
        ocr=OCRResult(text=plate or "", confidence=ocr_conf),
        processing_time_ms=500,
    )


def repository_with(collection):
    db = FakeDatabase({PLATE_RECOGNITIONS: collection})
    return RecognitionRepository(database_provider=lambda: db, clock=lambda: NOW)


def test_documento_minimo_sin_bbox_ni_imagen():
    doc = build_document("cap-1", response(), NOW)

    assert doc == {
        "captureId": "cap-1", "detectedPlate": "ABC-123", "confidence": 0.9,
        "processingTimeMs": 500, "status": "SUCCESS", "createdAt": NOW,
    }


def test_sin_ocr_la_confianza_es_cero():
    result = RecognitionResponse(status="NO_PLATE_DETECTED", message="-", processing_time_ms=300)

    doc = build_document(None, result, NOW)

    assert doc["confidence"] == 0.0 and doc["detectedPlate"] is None


@pytest.mark.asyncio
async def test_con_capture_id_hace_upsert_idempotente():
    collection = FakeCollection()

    saved = await repository_with(collection).save("cap-1", response())

    assert saved
    filter, update, upsert = collection.updates[0]
    assert filter == {"captureId": "cap-1"} and upsert
    # La fecha de creación no cambia si iot-service reintenta
    assert update["$setOnInsert"] == {"createdAt": NOW}
    assert "createdAt" not in update["$set"]


@pytest.mark.asyncio
async def test_sin_capture_id_inserta():
    collection = FakeCollection()

    await repository_with(collection).save(None, response())

    assert collection.inserts[0]["captureId"] is None


@pytest.mark.asyncio
async def test_sin_mongodb_no_falla():
    repository = RecognitionRepository(database_provider=lambda: None)

    assert await repository.save("cap-1", response()) is False


@pytest.mark.asyncio
async def test_error_de_mongodb_no_se_propaga():
    collection = FakeCollection(error=RuntimeError("conexión perdida"))

    assert await repository_with(collection).save("cap-1", response()) is False
