package handlers

import (
	"context"
	"encoding/base64"
	"net/http"
	"strings"
	"time"

	"github.com/edwinyoner/winner-systems-system-smart-parking/backend/services/iot-service/internal/models"
	"github.com/edwinyoner/winner-systems-system-smart-parking/backend/services/iot-service/internal/repository"
	"github.com/edwinyoner/winner-systems-system-smart-parking/backend/services/iot-service/internal/services"
	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"github.com/sirupsen/logrus"
)

// maxImageBytes: el mismo límite que aplica ia-service (10 MB)
const maxImageBytes = 10 * 1024 * 1024

// CaptureHandler maneja las capturas de las cámaras de entrada y salida (escenario A).
// Solo orquesta: guarda la foto, pide la lectura a ia-service y le pasa el resultado a
// parking-service, que es quien decide (registrar, "Por revisar", cobrar antes de salir).
type CaptureHandler struct {
	deviceRepo     *repository.DeviceRepository
	captureRepo    *repository.CaptureRepository
	iaClient       *services.IaClient
	parkingClient  *services.ParkingClient
	s3Service      *services.S3Service
	captureTimeout time.Duration
	logger         *logrus.Logger
}

// NewCaptureHandler crea el handler. captureTimeout es lo máximo que el ESP32 espera la respuesta.
func NewCaptureHandler(
	deviceRepo *repository.DeviceRepository,
	captureRepo *repository.CaptureRepository,
	iaClient *services.IaClient,
	parkingClient *services.ParkingClient,
	s3Service *services.S3Service,
	captureTimeout time.Duration,
	logger *logrus.Logger,
) *CaptureHandler {
	if captureTimeout <= 0 {
		captureTimeout = 15 * time.Second
	}
	return &CaptureHandler{
		deviceRepo:     deviceRepo,
		captureRepo:    captureRepo,
		iaClient:       iaClient,
		parkingClient:  parkingClient,
		s3Service:      s3Service,
		captureTimeout: captureTimeout,
		logger:         logger,
	}
}

// ============================================================================
// REQUEST/RESPONSE STRUCTS
// ============================================================================

// CreateCaptureRequest captura que envía la ESP32-CAM de entrada o salida.
// El parqueo y la zona salen del registro del dispositivo; parking_id es opcional y,
// si se envía, debe coincidir (así una cámara no puede registrar en otro parqueo).
type CreateCaptureRequest struct {
	DeviceID    string `json:"device_id" binding:"required"`
	ParkingID   uint64 `json:"parking_id,omitempty"`
	CaptureType string `json:"capture_type" binding:"required"` // "entry" o "exit" (sin importar mayúsculas)
	ImageBase64 string `json:"image_base64" binding:"required"`
	Timestamp   string `json:"timestamp,omitempty"`
}

// CreateCaptureResponse lo que recibe la cámara. open_gate dice si se levanta la barrera.
type CreateCaptureResponse struct {
	Success          bool     `json:"success"`
	CaptureID        string   `json:"capture_id"`
	ProcessingStatus string   `json:"processing_status"`
	Outcome          string   `json:"outcome,omitempty"`
	OpenGate         bool     `json:"open_gate"`
	Message          string   `json:"message"`
	PlateNumber      string   `json:"plate_number,omitempty"`
	Confidence       float64  `json:"confidence,omitempty"`
	TransactionID    *uint64  `json:"transaction_id,omitempty"`
	ReviewID         *uint64  `json:"review_id,omitempty"`
	SpaceCode        string   `json:"space_code,omitempty"`
	AmountDue        *float64 `json:"amount_due,omitempty"`
	Currency         string   `json:"currency,omitempty"`
}

// ErrorResponse respuesta de error estándar
type ErrorResponse struct {
	Success bool   `json:"success"`
	Error   string `json:"error"`
	Message string `json:"message"`
}

// ============================================================================
// ENDPOINTS
// ============================================================================

// CreateCapture maneja POST /api/v1/captures
// Flujo: ESP32-CAM -> iot-service -> ia-service -> parking-service -> respuesta a la cámara.
// Espera el resultado hasta captureTimeout; si tarda más, responde 202 y la cámara consulta
// GET /api/v1/captures/:captureId/status.
func (h *CaptureHandler) CreateCapture(c *gin.Context) {
	var req CreateCaptureRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Success: false, Error: "Invalid request", Message: err.Error()})
		return
	}

	direction, ok := normalizeDirection(req.CaptureType)
	if !ok {
		c.JSON(http.StatusBadRequest, ErrorResponse{
			Success: false, Error: "Invalid capture type", Message: "capture_type debe ser 'entry' o 'exit'",
		})
		return
	}

	device, status, problem := h.checkDevice(req)
	if problem != nil {
		c.JSON(status, *problem)
		return
	}

	imageData, err := base64.StdEncoding.DecodeString(req.ImageBase64)
	if err != nil || len(imageData) == 0 {
		c.JSON(http.StatusBadRequest, ErrorResponse{
			Success: false, Error: "Invalid image", Message: "La imagen no es un base64 válido",
		})
		return
	}
	if len(imageData) > maxImageBytes {
		c.JSON(http.StatusRequestEntityTooLarge, ErrorResponse{
			Success: false, Error: "Image too large", Message: "La imagen supera los 10 MB",
		})
		return
	}

	captureID := uuid.New().String()

	// La foto se guarda primero: el operador la necesita si la lectura va a "Por revisar"
	objectName := services.GenerateObjectName("captures", device.DeviceID, "jpg")
	imageURL, err := h.s3Service.UploadImage(c.Request.Context(), objectName, imageData, "image/jpeg")
	if err != nil {
		h.logger.WithError(err).Error("Error al subir imagen a S3")
		c.JSON(http.StatusInternalServerError, ErrorResponse{
			Success: false, Error: "Storage error", Message: "Error al almacenar imagen",
		})
		return
	}

	capture := &models.CameraCapture{
		CaptureID:        captureID,
		DeviceID:         device.DeviceID,
		ParkingID:        *device.ParkingID,
		ZoneID:           device.ZoneID,
		CaptureType:      direction,
		ImageURL:         imageURL,
		ProcessingStatus: models.ProcessingStatusPending,
	}
	if err := h.captureRepo.Create(capture); err != nil {
		h.logger.WithError(err).Error("Error al crear captura en DB")
		c.JSON(http.StatusInternalServerError, ErrorResponse{
			Success: false, Error: "Database error", Message: "Error al registrar captura",
		})
		return
	}

	// El procesamiento tiene su propio contexto: si la cámara deja de esperar, igual termina
	// y guarda el resultado (parking-service es idempotente por captureId).
	done := make(chan CreateCaptureResponse, 1)
	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), 3*h.captureTimeout)
		defer cancel()
		done <- h.processCapture(ctx, capture, imageData)
	}()

	select {
	case resp := <-done:
		code := http.StatusOK
		if !resp.Success {
			code = http.StatusBadGateway
		}
		c.JSON(code, resp)
	case <-time.After(h.captureTimeout):
		c.JSON(http.StatusAccepted, CreateCaptureResponse{
			Success:          true,
			CaptureID:        captureID,
			ProcessingStatus: string(models.ProcessingStatusProcessing),
			OpenGate:         false,
			Message:          "Procesando, consulte el estado de la captura",
		})
	}
}

// checkDevice valida que la cámara exista, esté activa, sea frontal y tenga parqueo
func (h *CaptureHandler) checkDevice(req CreateCaptureRequest) (*models.Device, int, *ErrorResponse) {
	device, err := h.deviceRepo.FindByDeviceID(req.DeviceID)
	if err != nil {
		return nil, http.StatusNotFound, &ErrorResponse{
			Success: false, Error: "Device not found", Message: "El dispositivo especificado no existe",
		}
	}
	if !device.IsActive() {
		return nil, http.StatusForbidden, &ErrorResponse{
			Success: false, Error: "Device inactive", Message: "El dispositivo no está activo",
		}
	}
	if device.DeviceType != models.DeviceTypeESP32CamFrontal {
		return nil, http.StatusForbidden, &ErrorResponse{
			Success: false, Error: "Wrong device type", Message: "Solo las cámaras de entrada y salida envían capturas",
		}
	}
	if device.ParkingID == nil {
		return nil, http.StatusConflict, &ErrorResponse{
			Success: false, Error: "Device without parking", Message: "La cámara no tiene un parqueo asignado",
		}
	}
	if req.ParkingID != 0 && req.ParkingID != *device.ParkingID {
		return nil, http.StatusForbidden, &ErrorResponse{
			Success: false, Error: "Parking mismatch", Message: "La cámara está registrada en otro parqueo",
		}
	}
	return device, 0, nil
}

// processCapture lee la placa y deja que parking-service decida. Si ia-service falla,
// la captura igual se envía sin placa: queda en "Por revisar" con la foto para el operador.
func (h *CaptureHandler) processCapture(ctx context.Context, capture *models.CameraCapture, image []byte) CreateCaptureResponse {
	log := h.logger.WithFields(logrus.Fields{"capture_id": capture.CaptureID, "device_id": capture.DeviceID})
	if err := h.captureRepo.MarkAsProcessing(capture.CaptureID); err != nil {
		log.WithError(err).Warn("No se pudo marcar la captura como en proceso")
	}

	plate, confidence := "", 0.0
	recognition, err := h.iaClient.RecognizePlate(ctx, services.PlateRecognitionRequest{
		Image: image, DeviceID: capture.DeviceID, CaptureID: capture.CaptureID,
	})
	if err != nil {
		log.WithError(err).Warn("ia-service no leyó la placa: se envía para revisión manual")
	} else {
		plate, confidence = recognition.PlateNumber, recognition.Confidence
	}

	result, err := h.parkingClient.ProcessCapture(ctx, services.CameraCaptureRequest{
		CaptureID:   capture.CaptureID,
		Direction:   string(capture.CaptureType),
		ParkingID:   capture.ParkingID,
		ZoneID:      capture.ZoneID,
		DeviceID:    capture.DeviceID,
		PlateNumber: plate,
		Confidence:  confidence,
		PhotoURL:    capture.ImageURL,
	})
	if err != nil {
		log.WithError(err).Error("parking-service no procesó la captura")
		_ = h.captureRepo.MarkAsFailed(capture.CaptureID, err.Error())
		return CreateCaptureResponse{
			Success:          false,
			CaptureID:        capture.CaptureID,
			ProcessingStatus: string(models.ProcessingStatusFailed),
			OpenGate:         false,
			Message:          "No se pudo registrar: llame al operador",
			PlateNumber:      plate,
			Confidence:       confidence,
		}
	}

	if err := h.captureRepo.MarkAsCompleted(capture.CaptureID, repository.CaptureResult{
		PlateNumber:   firstNonEmpty(result.PlateNumber, plate),
		Confidence:    confidence,
		Outcome:       result.Outcome,
		OpenGate:      result.OpenGate,
		Message:       result.Message,
		TransactionID: result.TransactionID,
		ReviewID:      result.ReviewID,
	}); err != nil {
		log.WithError(err).Error("No se pudo guardar el resultado de la captura")
	}

	log.WithFields(logrus.Fields{
		"outcome": result.Outcome, "open_gate": result.OpenGate, "confidence": confidence,
	}).Info("Captura procesada")

	return CreateCaptureResponse{
		Success:          true,
		CaptureID:        capture.CaptureID,
		ProcessingStatus: string(models.ProcessingStatusCompleted),
		Outcome:          result.Outcome,
		OpenGate:         result.OpenGate,
		Message:          result.Message,
		PlateNumber:      firstNonEmpty(result.PlateNumber, plate),
		Confidence:       confidence,
		TransactionID:    result.TransactionID,
		ReviewID:         result.ReviewID,
		SpaceCode:        result.SpaceCode,
		AmountDue:        result.AmountDue,
		Currency:         result.Currency,
	}
}

// normalizeDirection acepta "entry"/"ENTRY" y "exit"/"EXIT" (la columna MySQL es ENUM en mayúsculas)
func normalizeDirection(value string) (models.CaptureType, bool) {
	switch strings.ToUpper(strings.TrimSpace(value)) {
	case string(models.CaptureTypeEntry):
		return models.CaptureTypeEntry, true
	case string(models.CaptureTypeExit):
		return models.CaptureTypeExit, true
	}
	return "", false
}

func firstNonEmpty(values ...string) string {
	for _, v := range values {
		if v != "" {
			return v
		}
	}
	return ""
}

// GetCaptureStatus maneja GET /api/v1/captures/:captureId/status
func (h *CaptureHandler) GetCaptureStatus(c *gin.Context) {
	captureID := c.Param("captureId")

	capture, err := h.captureRepo.FindByCaptureID(captureID)
	if err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{
			Success: false,
			Error:   "Capture not found",
			Message: "Captura no encontrada",
		})
		return
	}

	response := CreateCaptureResponse{
		Success:          true,
		CaptureID:        capture.CaptureID,
		ProcessingStatus: string(capture.ProcessingStatus),
		OpenGate:         capture.OpenGate,
		TransactionID:    capture.TransactionID,
		ReviewID:         capture.ReviewID,
	}
	if capture.Outcome != nil {
		response.Outcome = *capture.Outcome
	}
	if capture.PlateNumber != nil {
		response.PlateNumber = *capture.PlateNumber
	}
	if capture.PlateConfidence != nil {
		response.Confidence = *capture.PlateConfidence
	}

	switch capture.ProcessingStatus {
	case models.ProcessingStatusCompleted:
		response.Message = "Captura procesada"
		if capture.ResultMessage != nil {
			response.Message = *capture.ResultMessage
		}
	case models.ProcessingStatusFailed:
		// El detalle técnico queda en la base; a la cámara solo le llega qué hacer
		response.Success = false
		response.Message = "No se pudo registrar: llame al operador"
	default:
		response.Message = "Procesando..."
	}

	c.JSON(http.StatusOK, response)
}

// GetCapturesByDevice maneja GET /api/v1/captures/device/:deviceId
func (h *CaptureHandler) GetCapturesByDevice(c *gin.Context) {
	deviceID := c.Param("deviceId")
	limit := 10 // Por defecto

	captures, err := h.captureRepo.FindByDeviceID(deviceID, limit)
	if err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{
			Success: false,
			Error:   "Database error",
			Message: "Error al obtener capturas",
		})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"success":  true,
		"captures": captures,
		"total":    len(captures),
	})
}
