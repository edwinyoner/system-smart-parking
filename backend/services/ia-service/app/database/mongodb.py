# ia-service/app/database/mongodb.py

"""
Conexión a MongoDB (motor, asíncrono).

ia-service sigue siendo sin estado: MongoDB solo guarda un registro mínimo de cada
reconocimiento (auditoría y métricas para la tesis). Si MongoDB no está disponible,
el reconocimiento funciona igual y solo se pierde ese registro.
"""
from typing import Optional

from motor.motor_asyncio import AsyncIOMotorClient, AsyncIOMotorDatabase

from app.core.config import settings
from app.core.logging import logger

# Colección del documento mínimo de CLAUDE.md §6.4
PLATE_RECOGNITIONS = "plate_recognitions"

_client: Optional[AsyncIOMotorClient] = None
_database: Optional[AsyncIOMotorDatabase] = None


async def connect_mongodb() -> bool:
    """
    Abre la conexión y crea los índices. Devuelve False (sin lanzar) si MongoDB
    está deshabilitado o no responde: el servicio arranca de todos modos.
    """
    global _client, _database
    if not settings.MONGODB_ENABLED:
        logger.info("MongoDB deshabilitado (MONGODB_ENABLED=false): no se guardarán reconocimientos")
        return False

    try:
        _client = AsyncIOMotorClient(
            settings.MONGODB_URL,
            minPoolSize=settings.MONGODB_MIN_POOL_SIZE,
            maxPoolSize=settings.MONGODB_MAX_POOL_SIZE,
            serverSelectionTimeoutMS=settings.MONGODB_TIMEOUT_MS,
        )
        await _client.admin.command("ping")
        _database = _client[settings.MONGODB_DB_NAME]
        await _ensure_indexes(_database)
        logger.info(f"MongoDB conectado (base {settings.MONGODB_DB_NAME})")
        return True
    except Exception as e:
        # No se registra la URL: puede llevar usuario y contraseña
        logger.warning(f"MongoDB no disponible, se sigue sin guardar reconocimientos: {type(e).__name__}")
        await close_mongodb()
        return False


async def _ensure_indexes(db: AsyncIOMotorDatabase) -> None:
    """
    - captureId único (solo cuando viene): un reintento de iot-service actualiza el mismo documento.
    - createdAt con TTL: las placas son datos personales, MongoDB las borra solo pasado el plazo.
    """
    collection = db[PLATE_RECOGNITIONS]
    await collection.create_index(
        "captureId",
        name="ux_capture_id",
        unique=True,
        partialFilterExpression={"captureId": {"$type": "string"}},
    )
    await collection.create_index(
        "createdAt",
        name="ttl_created_at",
        expireAfterSeconds=settings.MONGODB_RETENTION_DAYS * 24 * 3600,
    )


async def close_mongodb() -> None:
    """Cierra la conexión (al apagar el servicio)"""
    global _client, _database
    if _client is not None:
        _client.close()
    _client = None
    _database = None


def get_database() -> Optional[AsyncIOMotorDatabase]:
    """Base conectada o None si MongoDB no está disponible"""
    return _database


async def ping_mongodb() -> bool:
    """Para el health check"""
    if _client is None:
        return False
    try:
        await _client.admin.command("ping")
        return True
    except Exception:
        return False
