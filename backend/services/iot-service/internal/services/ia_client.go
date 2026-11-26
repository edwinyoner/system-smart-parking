package services

import (
	"bytes"
	"context"
	"fmt"
	"mime/multipart"
	"strings"
	"time"

	"github.com/edwinyoner/winner-systems-system-smart-parking/backend/services/iot-service/pkg/httpclient"
)

// IaClient cliente para comunicarse con ia-service
type IaClient struct {
	client *httpclient.Client
}

// NewIaClient crea una nueva instancia del cliente de IA
func NewIaClient(baseURL string) *IaClient {
	return &IaClient{
		client: httpclient.NewClient(httpclient.Config{
			BaseURL: baseURL,
			// YOLO + OCR tarda unos segundos; el límite total lo pone el contexto de la captura
			Timeout:    12 * time.Second,
			MaxRetries: 1,
		}),
	}
}

// ============================================================================
// RECONOCIMIENTO DE PLACAS (Entrada/Salida - ESCENARIO_02)
// ============================================================================

// PlateRecognitionRequest imagen a reconocer (JPEG en bytes, ya decodificada del base64 del ESP32)
type PlateRecognitionRequest struct {
	Image     []byte
	DeviceID  string // solo para el nombre del archivo y los logs
	CaptureID string // ia-service lo guarda en MongoDB para enlazar la lectura con la captura
}

// Estados que devuelve ia-service (app/core/constants.py)
const (
	IaStatusSuccess       = "SUCCESS"
	IaStatusLowConfidence = "LOW_CONFIDENCE"
)

// iaRecognitionResponse respuesta real de POST /api/v1/recognitions (RecognitionResponse en Python)
type iaRecognitionResponse struct {
	Status      string  `json:"status"`
	Message     string  `json:"message"`
	PlateNumber *string `json:"plate_number"`
	Detection   *struct {
		Confidence float64 `json:"confidence"`
	} `json:"detection"`
	OCR *struct {
		Text       string  `json:"text"`
		Confidence float64 `json:"confidence"`
	} `json:"ocr"`
	ProcessingTimeMS int `json:"processing_time_ms"`
}

// PlateRecognitionResponse lectura simplificada para iot-service.
// Sin placa (no se detectó o el OCR falló) PlateNumber queda vacío y Confidence en 0:
// parking-service la manda a "Por revisar" con la foto, no se descarta aquí.
type PlateRecognitionResponse struct {
	Status       string
	Message      string
	PlateNumber  string
	Confidence   float64 // confianza del OCR (0 a 1); el umbral de 0.85 lo aplica parking-service
	ProcessingMS int
}

// RecognizePlate envía la imagen como archivo (multipart/form-data, campo "image"),
// que es lo que espera ia-service.
// Endpoint: POST /api/v1/recognitions
func (c *IaClient) RecognizePlate(ctx context.Context, req PlateRecognitionRequest) (*PlateRecognitionResponse, error) {
	body, contentType, err := multipartImage(req.Image, req.DeviceID, req.CaptureID)
	if err != nil {
		return nil, err
	}

	resp, err := c.client.PostRaw(ctx, "/api/v1/recognitions", body, contentType, nil)
	if err != nil {
		return nil, fmt.Errorf("error al llamar a ia-service/recognitions: %w", err)
	}
	if !resp.IsSuccess() {
		return nil, fmt.Errorf("ia-service respondió HTTP %d: %s", resp.StatusCode, string(resp.Body))
	}

	var raw iaRecognitionResponse
	if err := resp.DecodeJSON(&raw); err != nil {
		return nil, fmt.Errorf("error al decodificar respuesta de ia-service: %w", err)
	}
	return toPlateRecognition(raw), nil
}

// toPlateRecognition reduce la respuesta de ia-service a lo que usa iot-service
func toPlateRecognition(raw iaRecognitionResponse) *PlateRecognitionResponse {
	result := &PlateRecognitionResponse{
		Status:       raw.Status,
		Message:      raw.Message,
		ProcessingMS: raw.ProcessingTimeMS,
	}
	if raw.PlateNumber != nil {
		result.PlateNumber = strings.TrimSpace(*raw.PlateNumber)
	} else if raw.OCR != nil {
		result.PlateNumber = strings.TrimSpace(raw.OCR.Text)
	}
	if raw.OCR != nil {
		result.Confidence = raw.OCR.Confidence
	}
	if result.PlateNumber == "" {
		result.Confidence = 0
	}
	return result
}

// multipartImage arma el formulario con la imagen. El nombre termina en .jpg porque
// ia-service valida la extensión del archivo.
func multipartImage(image []byte, deviceID, captureID string) ([]byte, string, error) {
	var buf bytes.Buffer
	w := multipart.NewWriter(&buf)
	name := "capture.jpg"
	if deviceID != "" {
		name = deviceID + ".jpg"
	}
	part, err := w.CreateFormFile("image", name)
	if err != nil {
		return nil, "", fmt.Errorf("error al preparar la imagen: %w", err)
	}
	if _, err := part.Write(image); err != nil {
		return nil, "", fmt.Errorf("error al preparar la imagen: %w", err)
	}
	if captureID != "" {
		if err := w.WriteField("capture_id", captureID); err != nil {
			return nil, "", fmt.Errorf("error al preparar la imagen: %w", err)
		}
	}
	if err := w.Close(); err != nil {
		return nil, "", fmt.Errorf("error al preparar la imagen: %w", err)
	}
	return buf.Bytes(), w.FormDataContentType(), nil
}

// ============================================================================
// PARKING VISION (Vista Aérea - ESCENARIO_01)
// ============================================================================

// ParkingVisionRequest estructura para detección de ocupación aérea
type ParkingVisionRequest struct {
	ImageBase64   string              `json:"image_base64"`   // Imagen aérea en base64
	ParkingID     uint64              `json:"parking_id"`     // ID del parking
	DeviceID      string              `json:"device_id"`      // ID del dispositivo
	SpacePolygons []SpacePolygonInput `json:"space_polygons"` // Polígonos de espacios
}

// SpacePolygonInput define un polígono de espacio de estacionamiento
type SpacePolygonInput struct {
	SpaceCode string      `json:"space_code"` // Código del espacio (ej: "A-001")
	Polygon   [][]float64 `json:"polygon"`    // Array de puntos [[x1,y1], [x2,y2], ...]
}

// ParkingVisionResponse respuesta de detección de ocupación
type ParkingVisionResponse struct {
	Success         bool                   `json:"success"`
	TotalSpaces     int                    `json:"total_spaces"`
	OccupiedSpaces  int                    `json:"occupied_spaces"`
	AvailableSpaces int                    `json:"available_spaces"`
	Detections      []SpaceDetectionResult `json:"detections"`
	ProcessingMS    int                    `json:"processing_ms"`
	Error           string                 `json:"error,omitempty"`
}

// SpaceDetectionResult resultado de detección por espacio
type SpaceDetectionResult struct {
	SpaceCode       string  `json:"space_code"`
	IsOccupied      bool    `json:"is_occupied"`
	Confidence      float64 `json:"confidence"`
	PlateNumber     string  `json:"plate_number,omitempty"` // Solo si está ocupado y se detectó placa
	PlateConfidence float64 `json:"plate_confidence,omitempty"`
}

// DetectOccupancy llama al endpoint de detección de ocupación aérea
// Endpoint: POST /api/v1/parking-vision
func (c *IaClient) DetectOccupancy(ctx context.Context, req ParkingVisionRequest) (*ParkingVisionResponse, error) {
	resp, err := c.client.Post(ctx, "/api/v1/parking-vision", req, nil)
	if err != nil {
		return nil, fmt.Errorf("error al llamar a ia-service/parking-vision: %w", err)
	}

	if !resp.IsSuccess() {
		return nil, resp.GetError()
	}

	var result ParkingVisionResponse
	if err := resp.DecodeJSON(&result); err != nil {
		return nil, fmt.Errorf("error al decodificar respuesta: %w", err)
	}

	return &result, nil
}

// ============================================================================
// HEALTH CHECKS
// ============================================================================

// HealthResponse respuesta de health check
type HealthResponse struct {
	Status  string `json:"status"`
	Service string `json:"service"`
	Version string `json:"version,omitempty"`
}

// CheckHealth verifica el estado del servicio de IA
// Endpoint: GET /api/v1/recognitions/status
func (c *IaClient) CheckHealth(ctx context.Context) (*HealthResponse, error) {
	resp, err := c.client.Get(ctx, "/api/v1/recognitions/status", nil)
	if err != nil {
		return nil, fmt.Errorf("error al verificar health de ia-service: %w", err)
	}

	if !resp.IsSuccess() {
		return nil, resp.GetError()
	}

	var result HealthResponse
	if err := resp.DecodeJSON(&result); err != nil {
		return nil, fmt.Errorf("error al decodificar respuesta: %w", err)
	}

	return &result, nil
}

// CheckParkingVisionHealth verifica el estado del módulo parking-vision
// Endpoint: GET /api/v1/parking-vision/health
func (c *IaClient) CheckParkingVisionHealth(ctx context.Context) (*HealthResponse, error) {
	resp, err := c.client.Get(ctx, "/api/v1/parking-vision/health", nil)
	if err != nil {
		return nil, fmt.Errorf("error al verificar health de parking-vision: %w", err)
	}

	if !resp.IsSuccess() {
		return nil, resp.GetError()
	}

	var result HealthResponse
	if err := resp.DecodeJSON(&result); err != nil {
		return nil, fmt.Errorf("error al decodificar respuesta: %w", err)
	}

	return &result, nil
}
