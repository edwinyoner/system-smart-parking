# ia-service/app/database/recognition_repository.py

"""
Registro mínimo de cada reconocimiento de placa (colección plate_recognitions).

Documento (CLAUDE.md §6.4), sin bbox, sin texto crudo del OCR ni la imagen:
    { captureId, detectedPlate, confidence, processingTimeMs, status, createdAt }

ia-service no conoce estadías ni transacciones: usedForEntry / transactionId los
decide parking-service y no se guardan aquí.
"""
from datetime import datetime, timezone
from typing import Callable, Optional

from motor.motor_asyncio import AsyncIOMotorDatabase

from app.core.logging import logger
from app.database.mongodb import PLATE_RECOGNITIONS, get_database
from app.schemas.recognition import RecognitionResponse


def build_document(capture_id: Optional[str], result: RecognitionResponse, now: datetime) -> dict:
    """Arma el documento mínimo a partir de la respuesta del reconocimiento"""
    return {
        "captureId": capture_id,
        "detectedPlate": result.plate_number,
        "confidence": result.ocr.confidence if result.ocr else 0.0,
        "processingTimeMs": result.processing_time_ms,
        "status": result.status,
        "createdAt": now,
    }


class RecognitionRepository:
    """Guarda los reconocimientos; nunca lanza: un fallo de MongoDB no afecta la lectura"""

    def __init__(
        self,
        database_provider: Callable[[], Optional[AsyncIOMotorDatabase]] = get_database,
        clock: Callable[[], datetime] = lambda: datetime.now(timezone.utc),
    ):
        self._database_provider = database_provider
        self._clock = clock

    async def save(self, capture_id: Optional[str], result: RecognitionResponse) -> bool:
        db = self._database_provider()
        if db is None:
            return False

        document = build_document(capture_id, result, self._clock())
        collection = db[PLATE_RECOGNITIONS]
        try:
            if capture_id:
                # Idempotente: si iot-service reintenta la misma captura, se actualiza el resultado
                created_at = document.pop("createdAt")
                await collection.update_one(
                    {"captureId": capture_id},
                    {"$set": document, "$setOnInsert": {"createdAt": created_at}},
                    upsert=True,
                )
            else:
                await collection.insert_one(document)
            return True
        except Exception as e:
            logger.warning(f"No se pudo guardar el reconocimiento en MongoDB: {type(e).__name__}")
            return False


def get_recognition_repository() -> RecognitionRepository:
    """Dependencia de FastAPI (se reemplaza en las pruebas)"""
    return RecognitionRepository()
