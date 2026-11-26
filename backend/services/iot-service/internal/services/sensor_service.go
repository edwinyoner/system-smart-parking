package services

import (
	"context"
	"errors"
	"fmt"
	"sync"
	"time"

	"github.com/edwinyoner/winner-systems-system-smart-parking/backend/services/iot-service/internal/models"
	"github.com/edwinyoner/winner-systems-system-smart-parking/backend/services/iot-service/internal/repository"
	"github.com/sirupsen/logrus"
)

// SensorPayload lo que publica el HC-SR04 en smartparking/sensors/{deviceId}/reading
type SensorPayload struct {
	DistanceCm float64 `json:"distance_cm"`
}

// ErrInvalidDistance lectura fuera del rango que mide el HC-SR04 (eco perdido o ruido)
var ErrInvalidDistance = errors.New("distancia fuera de rango")

// SensorThresholds umbrales para convertir distancia en ocupado / libre
type SensorThresholds struct {
	MinCm         float64 // por debajo: lectura inválida (el sensor no mide tan cerca)
	OccupiedMaxCm float64 // hasta esta distancia hay un vehículo encima del sensor
	MaxCm         float64 // alcance del HC-SR04 (unos 400 cm); por encima: lectura inválida
}

// Occupied decide si la distancia indica un vehículo
func (t SensorThresholds) Occupied(distanceCm float64) (bool, error) {
	maxCm := t.MaxCm
	if maxCm <= 0 {
		maxCm = 400
	}
	if distanceCm < t.MinCm || distanceCm > maxCm {
		return false, ErrInvalidDistance
	}
	return distanceCm <= t.OccupiedMaxCm, nil
}

// ============================================================================
// FILTRO DE LECTURAS (DEBOUNCE)
// ============================================================================

type sensorState struct {
	stable    *bool     // último estado confirmado (nil = aún no hay)
	candidate bool      // estado nuevo que se está observando
	since     time.Time // desde cuándo se observa el candidato
	watching  bool
}

// SensorTracker confirma un cambio solo si se mantiene durante `debounce`: una persona o
// un vehículo que pasa por encima del sensor no cambia el estado del espacio.
// Vive en memoria (una instancia de iot-service): si se reinicia, el primer estado estable
// se vuelve a enviar, y parking-service lo trata sin duplicar nada.
type SensorTracker struct {
	debounce time.Duration
	mu       sync.Mutex
	states   map[string]*sensorState
}

// NewSensorTracker crea el filtro
func NewSensorTracker(debounce time.Duration) *SensorTracker {
	return &SensorTracker{debounce: debounce, states: make(map[string]*sensorState)}
}

// Observe registra una lectura. Devuelve (estado, true) cuando hay que avisar un cambio.
func (t *SensorTracker) Observe(deviceID string, occupied bool, at time.Time) (bool, bool) {
	t.mu.Lock()
	defer t.mu.Unlock()

	st, ok := t.states[deviceID]
	if !ok {
		st = &sensorState{}
		t.states[deviceID] = st
	}

	// Coincide con lo confirmado: no hay cambio y se descarta cualquier candidato
	if st.stable != nil && *st.stable == occupied {
		st.watching = false
		return occupied, false
	}
	// Empieza a observar un estado nuevo
	if !st.watching || st.candidate != occupied {
		st.candidate, st.since, st.watching = occupied, at, true
	}
	if at.Sub(st.since) >= t.debounce {
		value := occupied
		st.stable, st.watching = &value, false
		return occupied, true
	}
	return occupied, false
}

// Forget olvida el estado confirmado (para reenviarlo si parking-service no lo recibió)
func (t *SensorTracker) Forget(deviceID string) {
	t.mu.Lock()
	defer t.mu.Unlock()
	delete(t.states, deviceID)
}

// ============================================================================
// SERVICIO DE SENSORES
// ============================================================================

// SensorService recibe las lecturas (por MQTT), filtra el ruido y avisa a parking-service
// los cambios estables. Luego pide republicar la disponibilidad del parqueo a los paneles.
type SensorService struct {
	devices    *repository.DeviceRepository
	readings   *repository.SensorReadingRepository
	parking    *ParkingClient
	tracker    *SensorTracker
	thresholds SensorThresholds
	onChange   func(parkingID uint64) // se llama tras un cambio aceptado (republicar paneles)
	logger     *logrus.Logger
	now        func() time.Time
}

// NewSensorService crea el servicio
func NewSensorService(devices *repository.DeviceRepository, readings *repository.SensorReadingRepository,
	parking *ParkingClient, tracker *SensorTracker, thresholds SensorThresholds, logger *logrus.Logger) *SensorService {
	return &SensorService{devices: devices, readings: readings, parking: parking, tracker: tracker,
		thresholds: thresholds, logger: logger, now: time.Now}
}

// OnChange registra a quién avisar cuando cambia la ocupación de un parqueo
func (s *SensorService) OnChange(fn func(parkingID uint64)) {
	s.onChange = fn
}

// HandleReading procesa una lectura del sensor deviceID
func (s *SensorService) HandleReading(ctx context.Context, deviceID string, payload SensorPayload) error {
	occupied, err := s.thresholds.Occupied(payload.DistanceCm)
	if err != nil {
		return err // ruido: no cambia nada
	}

	device, err := s.devices.FindByDeviceID(deviceID)
	if err != nil {
		return fmt.Errorf("sensor %s no registrado", deviceID)
	}
	if device.DeviceType != models.DeviceTypeSensorHCSR04 || !device.IsActive() {
		return fmt.Errorf("el dispositivo %s no es un sensor activo", deviceID)
	}
	go func() { _ = s.devices.UpdateHeartbeat(deviceID) }()

	state, changed := s.tracker.Observe(deviceID, occupied, s.now())
	if !changed {
		return nil
	}

	reading := &models.SensorReading{DeviceID: deviceID, ParkingID: device.ParkingID,
		DistanceCm: payload.DistanceCm, Occupied: state}
	result, err := s.parking.ProcessSensorReading(ctx, SensorReadingRequest{SensorID: deviceID, Occupied: state})
	if err != nil {
		msg := truncateText(err.Error(), 255)
		reading.Error = &msg
		if !errors.Is(err, ErrSensorNotAssigned) {
			// parking-service no lo recibió: se olvida para volver a enviarlo con la próxima lectura
			s.tracker.Forget(deviceID)
		}
	} else {
		reading.Outcome = &result.Outcome
		reading.SpaceCode = &result.SpaceCode
	}
	if saveErr := s.readings.Create(reading); saveErr != nil {
		s.logger.WithError(saveErr).Warn("No se pudo guardar la lectura del sensor")
	}
	if err != nil {
		return err
	}

	s.logger.WithFields(logrus.Fields{"sensor": deviceID, "occupied": state, "outcome": result.Outcome,
		"space": result.SpaceCode}).Info("Cambio de ocupación por sensor")
	if s.onChange != nil && device.ParkingID != nil {
		s.onChange(*device.ParkingID)
	}
	return nil
}

func truncateText(value string, max int) string {
	runes := []rune(value)
	if len(runes) <= max {
		return value
	}
	return string(runes[:max])
}
