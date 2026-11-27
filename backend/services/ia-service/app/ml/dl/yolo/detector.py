"""
Detector YOLO26 para reconocimiento de placas vehiculares
Implementación de CNN (Convolutional Neural Network) para detección de objetos
"""
from ultralytics import YOLO
import numpy as np
from typing import Optional, List, Dict, Tuple
from pathlib import Path
from datetime import datetime

from app.core.config import settings
from app.core.logging import logger
from app.core.exceptions import ModelLoadException, YOLODetectionException
from app.utils.formatters import format_bbox_dict


class YOLO26Detector:
    """
    Detector de placas vehiculares usando YOLO26

    YOLO (You Only Look Once) es una arquitectura de CNN que detecta objetos
    en una sola pasada por la red neuronal, haciéndola muy eficiente.

    Arquitectura:
    - Backbone: CSPDarknet (red convolucional profunda)
    - Neck: PANet (Feature Pyramid Network)
    - Head: Detection head sin NMS
    """

    def __init__(self):
        """Inicializa el detector YOLO26"""
        self.model: Optional[YOLO] = None
        # Modelo propio de placas si está configurado; si no, el general (COCO)
        self.model_path: str = settings.YOLO_PLATE_MODEL_PATH or settings.YOLO_MODEL_PATH
        self.dedicated: bool = bool(settings.YOLO_PLATE_MODEL_PATH)
        # IDs de las clases "placa" del modelo; None = se acepta cualquier clase
        self.plate_class_ids: Optional[List[int]] = None
        self.confidence_threshold: float = settings.YOLO_CONFIDENCE_THRESHOLD
        self.iou_threshold: float = settings.YOLO_IOU_THRESHOLD
        self.device: str = settings.YOLO_DEVICE
        self.is_loaded: bool = False
        self.load_time: Optional[datetime] = None

        logger.info("Inicializando YOLO26Detector")


    def load_model(self) -> None:
        """
        Carga el modelo YOLO26 en memoria

        Ultralytics descarga automáticamente el modelo si no existe.

        Raises:
            ModelLoadException: Si no se puede cargar el modelo
        """
        try:
            logger.info(f"Cargando modelo YOLO: {self.model_path}")
            logger.info("Si el modelo no existe, Ultralytics lo descargará automáticamente...")

            # Cargar modelo YOLO
            # Ultralytics descarga automáticamente si no existe
            self.model = YOLO(self.model_path)
            self.plate_class_ids = self._find_plate_classes(self.model.names)

            # Configurar dispositivo (CPU o GPU)
            if self.device == "cuda":
                logger.info("Usando GPU para inferencia YOLO")
            else:
                logger.info("Usando CPU para inferencia YOLO")

            self.is_loaded = True
            self.load_time = datetime.now()

            logger.info("Modelo YOLO26 cargado exitosamente")

        except Exception as e:
            logger.error(f"Error al cargar modelo YOLO: {str(e)}")
            raise ModelLoadException(
                message="No se pudo cargar el modelo YOLO",
                details={"error": str(e), "path": self.model_path}
            )


    @staticmethod
    def _find_plate_classes(names) -> Optional[List[int]]:
        """
        Busca en el modelo las clases que son placas (por nombre, sin importar mayúsculas).
        Con un modelo de placas solo se aceptan esas detecciones; con el general (COCO, sin
        clase placa) se devuelve None y se recorta el objeto de mayor confianza, como antes.
        """
        wanted = {n.strip().lower() for n in settings.YOLO_PLATE_CLASS_NAMES}
        items = names.items() if isinstance(names, dict) else enumerate(names)
        ids = sorted(int(i) for i, n in items if str(n).strip().lower() in wanted)
        if ids:
            logger.info(f"Modelo de placas: clases {ids} ({', '.join(str(names[i]) for i in ids)})")
            return ids
        logger.warning(
            "El modelo YOLO no tiene una clase de placa: se recorta el objeto de mayor confianza "
            "(suele ser el vehículo). Entrena uno con ai/plate-training y configura YOLO_PLATE_MODEL_PATH"
        )
        return None


    def detect_plates(
        self,
        image: np.ndarray,
        confidence_threshold: Optional[float] = None
    ) -> List[Dict]:
        """
        Detecta placas vehiculares en una imagen usando YOLO26

        Args:
            image: Imagen en formato numpy array (BGR)
            confidence_threshold: Umbral de confianza personalizado

        Returns:
            Lista de detecciones, cada una con:
            - bbox: {x, y, width, height}
            - confidence: float (0.0 - 1.0)

        Raises:
            YOLODetectionException: Si la detección falla
        """
        if not self.is_loaded:
            logger.info("Modelo no cargado, cargando automáticamente...")
            self.load_model()

        try:
            # Usar umbral personalizado o el configurado
            conf_threshold = confidence_threshold or self.confidence_threshold

            logger.debug(f"Ejecutando detección YOLO con confianza >= {conf_threshold}")

            # Inferencia YOLO
            results = self.model.predict(
                source=image,
                conf=conf_threshold,
                iou=self.iou_threshold,
                device=self.device,
                verbose=False,
                max_det=settings.YOLO_MAX_DETECTIONS,
                classes=self.plate_class_ids
            )

            # Procesar resultados
            detections = []

            for result in results:
                boxes = result.boxes

                if boxes is None or len(boxes) == 0:
                    logger.info("No se detectaron placas en la imagen")
                    continue

                for box in boxes:
                    # Extraer coordenadas (xyxy format)
                    x1, y1, x2, y2 = box.xyxy[0].cpu().numpy()

                    # Convertir a formato (x, y, width, height)
                    x = int(x1)
                    y = int(y1)
                    width = int(x2 - x1)
                    height = int(y2 - y1)

                    # Extraer confianza
                    confidence = float(box.conf[0].cpu().numpy())

                    detection = {
                        "bbox": format_bbox_dict(x, y, width, height),
                        "confidence": confidence
                    }

                    detections.append(detection)

                    logger.debug(
                        f"Placa detectada - bbox: ({x}, {y}, {width}, {height}), "
                        f"confianza: {confidence:.3f}"
                    )

            logger.info(f"Total de placas detectadas: {len(detections)}")

            return detections

        except Exception as e:
            logger.error(f"Error en detección YOLO: {str(e)}")
            raise YOLODetectionException(
                message="Error al detectar placas con YOLO",
                details={"error": str(e)}
            )


    def get_best_detection(
        self,
        image: np.ndarray,
        confidence_threshold: Optional[float] = None
    ) -> Optional[Dict]:
        """
        Obtiene la detección con mayor confianza

        Args:
            image: Imagen en formato numpy array
            confidence_threshold: Umbral de confianza personalizado

        Returns:
            Detección con mayor confianza o None si no hay detecciones
        """
        detections = self.detect_plates(image, confidence_threshold)

        if not detections:
            return None

        # Ordenar por confianza (mayor a menor)
        detections.sort(key=lambda x: x['confidence'], reverse=True)

        best_detection = detections[0]
        logger.info(f"Mejor detección con confianza: {best_detection['confidence']:.3f}")

        return best_detection


    def get_model_info(self) -> Dict:
        """
        Obtiene información sobre el modelo cargado

        Returns:
            Diccionario con información del modelo
        """
        info = {
            "loaded": self.is_loaded,
            "model_path": self.model_path,
            "dedicated_plate_model": self.dedicated,
            "plate_classes": self.plate_class_ids,
            "last_loaded": self.load_time.isoformat() if self.load_time else None,
            "device": self.device,
            "confidence_threshold": self.confidence_threshold,
            "iou_threshold": self.iou_threshold
        }

        return info