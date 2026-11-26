package handlers

import (
	"errors"
	"net/http"
	"strconv"
	"time"

	"github.com/edwinyoner/winner-systems-system-smart-parking/backend/services/iot-service/internal/models"
	"github.com/edwinyoner/winner-systems-system-smart-parking/backend/services/iot-service/internal/repository"
	"github.com/edwinyoner/winner-systems-system-smart-parking/backend/services/iot-service/internal/services"
	"github.com/gin-gonic/gin"
	"github.com/sirupsen/logrus"
)

// DisplayHandler maneja los paneles LED de disponibilidad
type DisplayHandler struct {
	deviceRepo     *repository.DeviceRepository
	displayLogRepo *repository.DisplayLogRepository
	availability   *services.AvailabilityService
	logger         *logrus.Logger
}

// NewDisplayHandler crea una nueva instancia del handler
func NewDisplayHandler(
	deviceRepo *repository.DeviceRepository,
	displayLogRepo *repository.DisplayLogRepository,
	availability *services.AvailabilityService,
	logger *logrus.Logger,
) *DisplayHandler {
	return &DisplayHandler{
		deviceRepo:     deviceRepo,
		displayLogRepo: displayLogRepo,
		availability:   availability,
		logger:         logger,
	}
}

// ============================================================================
// REQUEST/RESPONSE STRUCTS
// ============================================================================

// GetAvailabilityRequest solicitud del panel. El parqueo sale del registro del dispositivo;
// parking_id es opcional y, si se envía, debe coincidir.
type GetAvailabilityRequest struct {
	DeviceID  string `json:"device_id" binding:"required"`
	ParkingID uint64 `json:"parking_id,omitempty"`
}

// GetAvailabilityResponse lo que recibe el panel: la disponibilidad y el texto a mostrar
// Modos de panel
const (
	PanelModeParking = "PARKING" // junto a un parqueo: solo ese parqueo
	PanelModeAvenue  = "AVENUE"  // en una avenida: rota todos los parqueos, uno cada 10 s
)

// AvenueAvailabilityResponse respuesta para un panel de avenida
type AvenueAvailabilityResponse struct {
	Success bool   `json:"success"`
	Mode    string `json:"mode"`
	services.AvenuePanel
	CacheHit       bool  `json:"cache_hit"`
	ResponseTimeMS int64 `json:"response_time_ms"`
}

type GetAvailabilityResponse struct {
	Success bool   `json:"success"`
	Mode    string `json:"mode"`
	services.PanelAvailability
	CacheHit       bool  `json:"cache_hit"`
	ResponseTimeMS int64 `json:"response_time_ms"`
}

// ============================================================================
// ENDPOINTS
// ============================================================================

// GetAvailability maneja POST /api/v1/displays/availability (polling del panel cada 10 s).
// Con MQTT el panel recibe los cambios sin preguntar; este endpoint queda como respaldo.
func (h *DisplayHandler) GetAvailability(c *gin.Context) {
	startTime := time.Now()

	var req GetAvailabilityRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Success: false, Error: "Invalid request", Message: err.Error()})
		return
	}

	device, err := h.deviceRepo.FindByDeviceID(req.DeviceID)
	if err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Success: false, Error: "Device not found", Message: "Dispositivo no encontrado"})
		return
	}
	if !device.IsActive() {
		c.JSON(http.StatusForbidden, ErrorResponse{Success: false, Error: "Device inactive", Message: "El dispositivo no está activo"})
		return
	}
	if device.DeviceType != models.DeviceTypeESP32Display {
		c.JSON(http.StatusForbidden, ErrorResponse{
			Success: false, Error: "Wrong device type", Message: "Solo los paneles consultan la disponibilidad",
		})
		return
	}
	if device.IsAvenuePanel() {
		h.avenueAvailability(c, device, startTime)
		return
	}
	if req.ParkingID != 0 && req.ParkingID != *device.ParkingID {
		c.JSON(http.StatusForbidden, ErrorResponse{
			Success: false, Error: "Parking mismatch", Message: "El panel está registrado en otro parqueo",
		})
		return
	}
	parkingID := *device.ParkingID

	go h.deviceRepo.UpdateHeartbeat(device.DeviceID)

	panel, cacheHit, err := h.availability.Get(c.Request.Context(), parkingID)
	if errors.Is(err, services.ErrParkingNotFound) {
		c.JSON(http.StatusNotFound, ErrorResponse{Success: false, Error: "Parking not found", Message: "El parqueo no está publicado"})
		return
	}
	if err != nil {
		h.logger.WithError(err).Error("Error al obtener disponibilidad de parking-service")
		c.JSON(http.StatusServiceUnavailable, ErrorResponse{
			Success: false, Error: "Service unavailable", Message: "SIN DATOS",
		})
		return
	}

	response := GetAvailabilityResponse{
		Success:           true,
		Mode:              PanelModeParking,
		PanelAvailability: *panel,
		CacheHit:          cacheHit,
		ResponseTimeMS:    time.Since(startTime).Milliseconds(),
	}
	go h.logDisplayQuery(device.DeviceID, parkingID, response)
	c.JSON(http.StatusOK, response)
}

// logDisplayQuery registra la consulta en display_logs
// avenueAvailability panel sin parqueo: muestra los parqueos de su metadata (o todos)
func (h *DisplayHandler) avenueAvailability(c *gin.Context, device *models.Device, startTime time.Time) {
	go h.deviceRepo.UpdateHeartbeat(device.DeviceID)

	panel, cacheHit, err := h.availability.GetAvenue(c.Request.Context(), device.DeviceID, device.PanelParkingIDs(), device.PanelRotationSeconds())
	if err != nil {
		h.logger.WithError(err).Error("Error al obtener los parqueos para el panel de avenida")
		c.JSON(http.StatusServiceUnavailable, ErrorResponse{Success: false, Error: "Service unavailable", Message: "SIN DATOS"})
		return
	}
	response := AvenueAvailabilityResponse{Success: true, Mode: PanelModeAvenue, AvenuePanel: *panel,
		CacheHit: cacheHit, ResponseTimeMS: time.Since(startTime).Milliseconds()}

	// Una fila del historial por cada parqueo mostrado (la analítica es por parqueo)
	go func() {
		for _, p := range panel.Parkings {
			h.logDisplayQuery(device.DeviceID, p.ParkingID, GetAvailabilityResponse{
				PanelAvailability: services.PanelAvailability{TotalSpaces: p.TotalSpaces, AvailableSpaces: p.AvailableSpaces,
					OccupiedSpaces: max(p.TotalSpaces-p.AvailableSpaces, 0)},
				CacheHit: cacheHit, ResponseTimeMS: response.ResponseTimeMS})
		}
	}()
	c.JSON(http.StatusOK, response)
}

func (h *DisplayHandler) logDisplayQuery(deviceID string, parkingID uint64, response GetAvailabilityResponse) {
	// Convertir int64 a *int
	responseTimeMs := int(response.ResponseTimeMS)

	log := &models.DisplayLog{
		DeviceID:        deviceID,
		ParkingID:       parkingID,
		TotalSpaces:     response.TotalSpaces,
		AvailableSpaces: response.AvailableSpaces,
		OccupiedSpaces:  response.OccupiedSpaces,
		FromCache:       response.CacheHit,
		ResponseTimeMs:  &responseTimeMs, // Puntero a int
	}

	if err := h.displayLogRepo.Create(log); err != nil {
		h.logger.WithError(err).Error("Error al crear display log")
	}
}

// GetDisplayAnalytics maneja GET /api/v1/displays/analytics/:deviceId
func (h *DisplayHandler) GetDisplayAnalytics(c *gin.Context) {
	deviceID := c.Param("deviceId")

	// Parámetro opcional: hours (por defecto 24)
	hoursStr := c.DefaultQuery("hours", "24")
	hours, err := strconv.Atoi(hoursStr)
	if err != nil || hours <= 0 {
		hours = 24
	}

	since := time.Now().Add(-time.Duration(hours) * time.Hour)

	analytics, err := h.displayLogRepo.GetAnalyticsByDevice(deviceID, since)
	if err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{
			Success: false,
			Error:   "Database error",
			Message: "Error al obtener analytics",
		})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"success":      true,
		"device_id":    deviceID,
		"period_hours": hours,
		"analytics":    analytics,
	})
}

// GetParkingAnalytics maneja GET /api/v1/displays/analytics/parking/:parkingId
func (h *DisplayHandler) GetParkingAnalytics(c *gin.Context) {
	parkingIDStr := c.Param("parkingId")
	parkingID, err := strconv.ParseUint(parkingIDStr, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{
			Success: false,
			Error:   "Invalid parking ID",
			Message: "ID de parking inválido",
		})
		return
	}

	// Parámetro opcional: hours (por defecto 24)
	hoursStr := c.DefaultQuery("hours", "24")
	hours, err := strconv.Atoi(hoursStr)
	if err != nil || hours <= 0 {
		hours = 24
	}

	since := time.Now().Add(-time.Duration(hours) * time.Hour)

	analytics, err := h.displayLogRepo.GetAnalyticsByParkingID(parkingID, since)
	if err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{
			Success: false,
			Error:   "Database error",
			Message: "Error al obtener analytics",
		})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"success":      true,
		"parking_id":   parkingID,
		"period_hours": hours,
		"analytics":    analytics,
	})
}

// InvalidateCache maneja DELETE /api/v1/displays/cache/:parkingId
// Endpoint administrativo para invalidar cache manualmente
func (h *DisplayHandler) InvalidateCache(c *gin.Context) {
	parkingIDStr := c.Param("parkingId")
	parkingID, err := strconv.ParseUint(parkingIDStr, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{
			Success: false,
			Error:   "Invalid parking ID",
			Message: "ID de parking inválido",
		})
		return
	}

	err = h.availability.Invalidate(c.Request.Context(), parkingID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{
			Success: false,
			Error:   "Cache error",
			Message: "Error al invalidar cache",
		})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"success":    true,
		"message":    "Cache invalidado exitosamente",
		"parking_id": parkingID,
	})
}
