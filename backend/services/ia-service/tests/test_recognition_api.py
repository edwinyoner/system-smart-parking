# ia-service/tests/test_recognition_api.py

"""Pruebas del endpoint POST /api/v1/recognitions con el modelo reemplazado"""
import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.database.recognition_repository import get_recognition_repository
from app.schemas.recognition import DetectionResult, OCRResult, RecognitionResponse
from app.schemas.base import BoundingBox
from app.services.plate_recognition_service import get_plate_recognition_service


class FakePlateService:
    """Responde una lectura fija sin cargar YOLO ni EasyOCR"""

    def __init__(self, response=None, error=None):
        self.response = response
        self.error = error
        self.calls = 0

    def recognize_plate(self, image, request):
        self.calls += 1
        if self.error:
            raise self.error
        return self.response

    def get_service_status(self):
        return {"service": "PlateRecognitionService", "status": "OK"}


class FakeRepository:
    def __init__(self):
        self.saved = []

    async def save(self, capture_id, result):
        self.saved.append((capture_id, result))
        return True


def success_response() -> RecognitionResponse:
    return RecognitionResponse(
        status="SUCCESS",
        message="Placa vehicular reconocida exitosamente",
        plate_number="ABC-123",
        detection=DetectionResult(bbox=BoundingBox(x=10, y=10, width=80, height=30), confidence=0.93),
        ocr=OCRResult(text="ABC-123", confidence=0.88),
        processing_time_ms=740,
    )


@pytest.fixture
def client_with():
    """Crea un cliente con el servicio y el repositorio falsos (sin ejecutar el lifespan)"""
    def build(service, repository):
        app.dependency_overrides[get_plate_recognition_service] = lambda: service
        app.dependency_overrides[get_recognition_repository] = lambda: repository
        return TestClient(app)

    yield build
    app.dependency_overrides.clear()


def test_reconoce_y_guarda_el_registro_minimo(client_with, jpeg_bytes):
    service, repository = FakePlateService(success_response()), FakeRepository()
    client = client_with(service, repository)

    resp = client.post(
        "/api/v1/recognitions",
        files={"image": ("CAM-1.jpg", jpeg_bytes, "image/jpeg")},
        data={"capture_id": "cap-001"},
    )

    assert resp.status_code == 200
    body = resp.json()
    # Contrato que lee iot-service (ia_client.go)
    assert body["status"] == "SUCCESS"
    assert body["plate_number"] == "ABC-123"
    assert body["ocr"]["confidence"] == 0.88
    assert body["processing_time_ms"] == 740
    assert service.calls == 1
    assert repository.saved[0][0] == "cap-001"


def test_sin_capture_id_tambien_se_guarda(client_with, jpeg_bytes):
    repository = FakeRepository()
    client = client_with(FakePlateService(success_response()), repository)

    resp = client.post("/api/v1/recognitions", files={"image": ("x.jpg", jpeg_bytes, "image/jpeg")})

    assert resp.status_code == 200
    assert repository.saved[0][0] is None


def test_formato_de_archivo_no_permitido(client_with, jpeg_bytes):
    service = FakePlateService(success_response())
    client = client_with(service, FakeRepository())

    resp = client.post("/api/v1/recognitions", files={"image": ("nota.txt", b"hola", "text/plain")})

    assert resp.status_code == 400
    assert service.calls == 0


def test_error_interno_no_expone_detalles(client_with, jpeg_bytes):
    service = FakePlateService(error=RuntimeError("ruta secreta /opt/modelo.pt"))
    client = client_with(service, FakeRepository())

    resp = client.post("/api/v1/recognitions", files={"image": ("x.jpg", jpeg_bytes, "image/jpeg")})

    assert resp.status_code == 500
    assert "secreta" not in resp.text


def test_capture_id_demasiado_largo(client_with, jpeg_bytes):
    client = client_with(FakePlateService(success_response()), FakeRepository())

    resp = client.post(
        "/api/v1/recognitions",
        files={"image": ("x.jpg", jpeg_bytes, "image/jpeg")},
        data={"capture_id": "x" * 65},
    )

    assert resp.status_code == 422


def test_health_indica_mongodb_no_disponible():
    resp = TestClient(app).get("/api/health")

    assert resp.status_code == 200
    assert resp.json()["mongodb"] == "unavailable"
