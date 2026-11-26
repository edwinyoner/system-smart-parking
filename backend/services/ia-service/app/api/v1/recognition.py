"""
Endpoints REST para reconocimiento de placas vehiculares
API Version 1
"""
from fastapi import APIRouter, BackgroundTasks, Depends, UploadFile, File, HTTPException, Form
from fastapi.concurrency import run_in_threadpool
from typing import Optional

from app.core.logging import logger
from app.core.constants import MSG_INVALID_IMAGE, MSG_IMAGE_TOO_LARGE
from app.core.exceptions import (
    ImageProcessingException,
    YOLODetectionException,
    OCRException
)
from app.schemas.recognition import RecognitionResponse, RecognitionRequest
from app.services.plate_recognition_service import (
    PlateRecognitionService,
    get_plate_recognition_service
)
from app.database.recognition_repository import (
    RecognitionRepository,
    get_recognition_repository
)
from app.utils.image_utils import (
    read_image_from_bytes,
    validate_image_format,
    validate_image_size
)

# Crear router con versionado y recurso en plural
router = APIRouter(
    prefix="/recognitions",
    tags=["Plate Recognition"]
)

# El servicio (YOLO + EasyOCR) llega por Depends: se carga una sola vez y en las
# pruebas se reemplaza por uno falso, sin cargar los modelos.


@router.post(
    "",
    response_model=RecognitionResponse,
    summary="Reconocer placa vehicular",
    description="""
    Reconoce una placa vehicular en una imagen usando Deep Learning.

    Pipeline:
    1. Detección de placa con YOLO26 (CNN)
    2. Lectura de texto con EasyOCR (CNN + RNN)
    3. Validación y normalización de formato

    Formatos de imagen soportados: JPG, JPEG, PNG
    Tamaño máximo: 10 MB

    **Endpoint:** POST /api/v1/recognitions
    """
)
async def recognize_plate(
        background_tasks: BackgroundTasks,
        image: UploadFile = File(..., description="Imagen con placa vehicular"),
        yolo_confidence: Optional[float] = Form(
            default=0.25,
            ge=0.0,
            le=1.0,
            description="Umbral de confianza para YOLO (0.0 - 1.0)"
        ),
        ocr_confidence: Optional[float] = Form(
            default=0.5,
            ge=0.0,
            le=1.0,
            description="Umbral de confianza para OCR (0.0 - 1.0)"
        ),
        capture_id: Optional[str] = Form(
            default=None,
            max_length=64,
            description="ID de la captura en iot-service (para el registro en MongoDB)"
        ),
        plate_service: PlateRecognitionService = Depends(get_plate_recognition_service),
        repository: RecognitionRepository = Depends(get_recognition_repository)
) -> RecognitionResponse:
    """
    Endpoint principal para reconocer placas vehiculares

    Args:
        image: Archivo de imagen (JPG, PNG)
        yolo_confidence: Confianza mínima para detección YOLO
        ocr_confidence: Confianza mínima para lectura OCR

    Returns:
        RecognitionResponse con placa reconocida y métricas

    Raises:
        HTTPException 400: Si la imagen es inválida
        HTTPException 413: Si la imagen es demasiado grande
        HTTPException 500: Si ocurre un error en el procesamiento
    """
    logger.info(f"Nueva petición de reconocimiento - archivo: {image.filename}")

    try:
        # Validar formato de archivo
        if not validate_image_format(image.filename):
            logger.warning(f"Formato de imagen no válido: {image.filename}")
            raise HTTPException(
                status_code=400,
                detail=MSG_INVALID_IMAGE
            )

        # Leer bytes de la imagen
        image_bytes = await image.read()

        # Validar tamaño
        if not validate_image_size(image_bytes):
            size_mb = len(image_bytes) / (1024 * 1024)
            logger.warning(f"Imagen demasiado grande: {size_mb:.2f} MB")
            raise HTTPException(
                status_code=413,
                detail=MSG_IMAGE_TOO_LARGE
            )

        # Convertir a formato OpenCV
        image_np = read_image_from_bytes(image_bytes)

        logger.info(f"Imagen cargada: {image_np.shape}")

        # Crear request con parámetros
        request = RecognitionRequest(
            yolo_confidence=yolo_confidence,
            ocr_confidence=ocr_confidence
        )

        # YOLO + EasyOCR usan CPU varios segundos: se ejecutan en un hilo aparte para no
        # bloquear el bucle de eventos (si no, el servicio no atiende otras peticiones)
        result = await run_in_threadpool(plate_service.recognize_plate, image_np, request)

        # Registro mínimo en MongoDB después de responder: no retrasa a la cámara
        background_tasks.add_task(repository.save, capture_id, result)

        logger.info(
            f"Reconocimiento completado - Status: {result.status}, "
            f"Placa: {result.plate_number}, "
            f"Tiempo: {result.processing_time_ms}ms"
        )

        return result

    except ImageProcessingException as e:
        logger.error(f"Error procesando imagen: {e.message}")
        raise HTTPException(status_code=400, detail=e.message)

    except (YOLODetectionException, OCRException) as e:
        logger.error(f"Error en modelos de Deep Learning: {e.message}")
        raise HTTPException(status_code=500, detail="Error en los modelos de reconocimiento")

    except HTTPException:
        # Re-lanzar HTTPExceptions sin modificar
        raise

    except Exception as e:
        # El detalle queda en el log; al cliente no se le exponen errores internos
        logger.exception(f"Error inesperado en reconocimiento: {str(e)}")
        raise HTTPException(status_code=500, detail="Error interno del servidor")


@router.get(
    "/status",
    summary="Estado del servicio de reconocimiento",
    description="""
    Obtiene el estado de los modelos YOLO26 y EasyOCR.

    **Endpoint:** GET /api/v1/recognitions/status
    """
)
async def get_recognition_status(
        plate_service: PlateRecognitionService = Depends(get_plate_recognition_service)
):
    """
    Obtiene el estado del servicio y los modelos de Deep Learning

    Returns:
        Estado de YOLO26 y EasyOCR (cargados, rutas, tamaños)
    """
    try:
        status = plate_service.get_service_status()
        logger.debug("Estado del servicio solicitado")
        return status

    except Exception as e:
        logger.error(f"Error obteniendo estado: {str(e)}")
        raise HTTPException(
            status_code=500,
            detail="Error al obtener el estado de los modelos"
        )