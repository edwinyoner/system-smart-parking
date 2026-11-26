package services

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"time"

	"github.com/edwinyoner/winner-systems-system-smart-parking/backend/services/iot-service/pkg/httpclient"
)

// ParkingClient cliente para comunicarse con parking-service (por la red interna, sin gateway)
type ParkingClient struct {
	client *httpclient.Client
	tokens *ServiceTokenProvider
}

// NewParkingClient crea el cliente. tokens entrega el token de servicio de auth-service.
func NewParkingClient(baseURL string, tokens *ServiceTokenProvider) *ParkingClient {
	return &ParkingClient{
		client: httpclient.NewClient(httpclient.Config{
			BaseURL:    baseURL,
			Timeout:    10 * time.Second,
			MaxRetries: 2,
		}),
		tokens: tokens,
	}
}

// ============================================================================
// CAPTURAS DE CÁMARA - ENTRADA Y SALIDA (ESCENARIO A)
// ============================================================================

// CameraCaptureRequest lectura de una cámara, ya procesada por ia-service.
// parking-service decide: registra, manda a "Por revisar" (confianza < 0.85, placa inválida...)
// o pide pagar. Es idempotente por captureId: reintentar no duplica la entrada.
type CameraCaptureRequest struct {
	CaptureID   string  `json:"captureId"`
	Direction   string  `json:"direction"` // ENTRY o EXIT
	ParkingID   uint64  `json:"parkingId"`
	ZoneID      *uint64 `json:"zoneId,omitempty"`
	DeviceID    string  `json:"deviceId"`
	PlateNumber string  `json:"plateNumber,omitempty"`
	Confidence  float64 `json:"confidence"`
	PhotoURL    string  `json:"photoUrl,omitempty"`
}

// Resultados posibles de CameraCaptureResult.Outcome
const (
	OutcomeEntryRegistered = "ENTRY_REGISTERED"
	OutcomeExitRegistered  = "EXIT_REGISTERED"
	OutcomePaymentRequired = "PAYMENT_REQUIRED"
	OutcomePendingReview   = "PENDING_REVIEW"
	OutcomeRejected        = "REJECTED"
)

// CameraCaptureResult lo que decidió parking-service. OpenGate dice si se abre la barrera.
type CameraCaptureResult struct {
	Outcome       string   `json:"outcome"`
	OpenGate      bool     `json:"openGate"`
	Message       string   `json:"message"`
	PlateNumber   string   `json:"plateNumber"`
	TransactionID *uint64  `json:"transactionId"`
	ReviewID      *uint64  `json:"reviewId"`
	SpaceCode     string   `json:"spaceCode"`
	AmountDue     *float64 `json:"amountDue"`
	Currency      string   `json:"currency"`
}

// ProcessCapture envía la lectura a parking-service.
// Endpoint: POST /internal/captures (exige token de servicio con internal.captures.process)
func (c *ParkingClient) ProcessCapture(ctx context.Context, req CameraCaptureRequest) (*CameraCaptureResult, error) {
	resp, err := c.postWithToken(ctx, "/internal/captures", req)
	if err != nil {
		return nil, err
	}
	if !resp.IsSuccess() {
		return nil, fmt.Errorf("parking-service rechazó la captura (HTTP %d): %s", resp.StatusCode, string(resp.Body))
	}

	var result CameraCaptureResult
	if err := resp.DecodeJSON(&result); err != nil {
		return nil, fmt.Errorf("error al decodificar la respuesta de parking-service: %w", err)
	}
	return &result, nil
}

// postWithToken agrega el token de servicio; si parking-service responde 401 (token vencido
// o rotado), pide uno nuevo y reintenta una sola vez.
func (c *ParkingClient) postWithToken(ctx context.Context, path string, body interface{}) (*httpclient.Response, error) {
	for attempt := 0; attempt < 2; attempt++ {
		token, err := c.tokens.Token(ctx)
		if err != nil {
			return nil, err
		}
		resp, err := c.client.Post(ctx, path, body, map[string]string{"Authorization": "Bearer " + token})
		if err != nil {
			return nil, fmt.Errorf("parking-service no respondió: %w", err)
		}
		if resp.StatusCode == http.StatusUnauthorized && attempt == 0 {
			c.tokens.Invalidate()
			continue
		}
		return resp, nil
	}
	return nil, fmt.Errorf("parking-service rechazó el token de servicio")
}

// ============================================================================
// ESPACIOS - ACTUALIZACIÓN MASIVA (ESCENARIO_01)
// ============================================================================

// BulkUpdateOccupancyRequest actualización masiva de ocupación
type BulkUpdateOccupancyRequest struct {
	ParkingID  uint64                 `json:"parkingId"`
	SnapshotID string                 `json:"snapshotId"`
	DeviceID   string                 `json:"deviceId"`
	Spaces     []SpaceOccupancyUpdate `json:"spaces"`
}

// SpaceOccupancyUpdate actualización de ocupación de un espacio
type SpaceOccupancyUpdate struct {
	SpaceCode   string  `json:"spaceCode"`
	IsOccupied  bool    `json:"isOccupied"`
	Confidence  float64 `json:"confidence"`
	PlateNumber string  `json:"plateNumber,omitempty"`
	ImageURL    string  `json:"imageUrl,omitempty"`
}

// BulkUpdateOccupancyResponse respuesta de actualización masiva
type BulkUpdateOccupancyResponse struct {
	Success        bool   `json:"success"`
	UpdatedSpaces  int    `json:"updatedSpaces"`
	OccupiedCount  int    `json:"occupiedCount"`
	AvailableCount int    `json:"availableCount"`
	Message        string `json:"message"`
	Error          string `json:"error,omitempty"`
}

// BulkUpdateOccupancy actualiza la ocupación de múltiples espacios.
// PENDIENTE: parking-service aún no tiene este endpoint (ocupación por visión, escenario B).
// Endpoint: PUT /api/v1/spaces/bulk-occupancy
func (c *ParkingClient) BulkUpdateOccupancy(ctx context.Context, req BulkUpdateOccupancyRequest) (*BulkUpdateOccupancyResponse, error) {
	headers := map[string]string{
		// "Authorization": "Bearer " + token,
	}

	resp, err := c.client.Put(ctx, "/api/v1/spaces/bulk-occupancy", req, headers)
	if err != nil {
		return nil, fmt.Errorf("error al llamar a parking-service/spaces/bulk-occupancy: %w", err)
	}

	if !resp.IsSuccess() {
		return nil, resp.GetError()
	}

	var result BulkUpdateOccupancyResponse
	if err := resp.DecodeJSON(&result); err != nil {
		return nil, fmt.Errorf("error al decodificar respuesta: %w", err)
	}

	return &result, nil
}

// ============================================================================
// SENSORES HC-SR04 (OCUPACIÓN POR ESPACIO)
// ============================================================================

// SensorReadingRequest lectura estable de un sensor (sensorId = device_id = Space.sensorId)
type SensorReadingRequest struct {
	SensorID string `json:"sensorId"`
	Occupied bool   `json:"occupied"`
}

// SensorReadingResult qué hizo parking-service (SPACE_OCCUPIED, SPACE_RELEASED, STAY_MOVED, NO_CHANGE)
type SensorReadingResult struct {
	Outcome       string  `json:"outcome"`
	SpaceID       *uint64 `json:"spaceId"`
	SpaceCode     string  `json:"spaceCode"`
	SpaceStatus   string  `json:"spaceStatus"`
	TransactionID *uint64 `json:"transactionId"`
	Message       string  `json:"message"`
}

// ErrSensorNotAssigned ningún espacio de parking-service tiene ese sensor
var ErrSensorNotAssigned = errors.New("el sensor no está asignado a ningún espacio")

// ProcessSensorReading envía la lectura a parking-service.
// Endpoint: POST /internal/sensors/readings (token de servicio con internal.sensors.process)
func (c *ParkingClient) ProcessSensorReading(ctx context.Context, req SensorReadingRequest) (*SensorReadingResult, error) {
	resp, err := c.postWithToken(ctx, "/internal/sensors/readings", req)
	if err != nil {
		return nil, err
	}
	if resp.StatusCode == http.StatusNotFound {
		return nil, ErrSensorNotAssigned
	}
	if !resp.IsSuccess() {
		return nil, fmt.Errorf("parking-service rechazó la lectura (HTTP %d): %s", resp.StatusCode, string(resp.Body))
	}
	var result SensorReadingResult
	if err := resp.DecodeJSON(&result); err != nil {
		return nil, fmt.Errorf("error al decodificar la respuesta de parking-service: %w", err)
	}
	return &result, nil
}

// ============================================================================
// DISPONIBILIDAD PÚBLICA (PANELES LED)
// ============================================================================

// ZoneAvailability espacios libres de una zona (solo cuenta las que reciben vehículos)
type ZoneAvailability struct {
	Code            string `json:"code"`
	Name            string `json:"name"`
	Operational     bool   `json:"operational"`
	TotalSpaces     int    `json:"totalSpaces"`
	AvailableSpaces int    `json:"availableSpaces"`
}

// ParkingAvailability respuesta de GET /public/parkings/{id}/availability.
// AvailableSpaces es 0 si el parqueo no está operativo o no atiende a esta hora.
type ParkingAvailability struct {
	ParkingID       uint64             `json:"parkingId"`
	Code            string             `json:"code"`
	Name            string             `json:"name"`
	Status          string             `json:"status"`
	Operational     bool               `json:"operational"`
	OpenNow         bool               `json:"openNow"`
	TotalSpaces     int                `json:"totalSpaces"`
	AvailableSpaces int                `json:"availableSpaces"`
	Zones           []ZoneAvailability `json:"zones"`
	GeneratedAt     string             `json:"generatedAt"`
}

// ErrParkingNotFound el parqueo no existe, está eliminado o desactivado
var ErrParkingNotFound = errors.New("parqueo no encontrado")

// GetAvailability consulta la disponibilidad pública de un parqueo (no necesita token).
// Endpoint: GET /public/parkings/{parkingId}/availability
func (c *ParkingClient) GetAvailability(ctx context.Context, parkingID uint64) (*ParkingAvailability, error) {
	resp, err := c.client.Get(ctx, fmt.Sprintf("/public/parkings/%d/availability", parkingID), nil)
	if err != nil {
		return nil, fmt.Errorf("parking-service no respondió: %w", err)
	}
	if resp.StatusCode == http.StatusNotFound {
		return nil, ErrParkingNotFound
	}
	if !resp.IsSuccess() {
		return nil, fmt.Errorf("parking-service respondió HTTP %d", resp.StatusCode)
	}

	var result ParkingAvailability
	if err := resp.DecodeJSON(&result); err != nil {
		return nil, fmt.Errorf("error al decodificar la disponibilidad: %w", err)
	}
	return &result, nil
}
