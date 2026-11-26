package services

import (
	"context"
	"encoding/json"
	"fmt"
	"sync"
	"time"

	"github.com/edwinyoner/winner-systems-system-smart-parking/backend/services/iot-service/internal/models"
	"github.com/edwinyoner/winner-systems-system-smart-parking/backend/services/iot-service/internal/repository"
	"github.com/sirupsen/logrus"
)

// AvailabilityTopic tema MQTT donde los paneles de un parqueo reciben la disponibilidad
func AvailabilityTopic(parkingID uint64) string {
	return fmt.Sprintf("smartparking/parkings/%d/availability", parkingID)
}

// Publisher lo mínimo que necesita el publicador del cliente MQTT (facilita las pruebas)
type Publisher interface {
	Publish(topic string, payload []byte, retained bool) error
}

// AvailabilityPublisher publica la disponibilidad de los parqueos que tienen paneles.
// Solo publica cuando cambia (mensaje retenido: un panel que se conecta recibe el último valor
// al instante). Revisa cada `interval` y también al momento si un sensor cambió algo.
type AvailabilityPublisher struct {
	devices      *repository.DeviceRepository
	availability *AvailabilityService
	publisher    Publisher
	interval     time.Duration
	logger       *logrus.Logger

	mu   sync.Mutex
	last map[uint64]string // último contenido publicado por parqueo (sin la hora)
	wake chan uint64
}

// NewAvailabilityPublisher crea el publicador
func NewAvailabilityPublisher(devices *repository.DeviceRepository, availability *AvailabilityService,
	publisher Publisher, interval time.Duration, logger *logrus.Logger) *AvailabilityPublisher {
	if interval <= 0 {
		interval = 5 * time.Second
	}
	return &AvailabilityPublisher{devices: devices, availability: availability, publisher: publisher,
		interval: interval, logger: logger, last: make(map[uint64]string), wake: make(chan uint64, 32)}
}

// Run publica hasta que se cancele el contexto
func (p *AvailabilityPublisher) Run(ctx context.Context) {
	ticker := time.NewTicker(p.interval)
	defer ticker.Stop()
	p.publishAll(ctx)
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			p.publishAll(ctx)
		case parkingID := <-p.wake:
			_ = p.availability.Invalidate(ctx, parkingID)
			p.publishOne(ctx, parkingID)
		}
	}
}

// Notify pide publicar ya un parqueo (por ejemplo, tras un cambio de sensor)
func (p *AvailabilityPublisher) Notify(parkingID uint64) {
	select {
	case p.wake <- parkingID:
	default: // ya hay avisos pendientes: el próximo ciclo lo cubre
	}
}

// Reset olvida lo publicado: tras reconectar al broker se republica todo
func (p *AvailabilityPublisher) Reset() {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.last = make(map[uint64]string)
}

// parkingsWithPanels parqueos que tienen al menos un panel activo
func (p *AvailabilityPublisher) parkingsWithPanels() []uint64 {
	panels, err := p.devices.FindByType(models.DeviceTypeESP32Display)
	if err != nil {
		p.logger.WithError(err).Warn("No se pudieron leer los paneles")
		return nil
	}
	seen := make(map[uint64]bool)
	var ids []uint64
	for _, d := range panels {
		if d.IsActive() && d.ParkingID != nil && !seen[*d.ParkingID] {
			seen[*d.ParkingID] = true
			ids = append(ids, *d.ParkingID)
		}
	}
	return ids
}

func (p *AvailabilityPublisher) publishAll(ctx context.Context) {
	for _, id := range p.parkingsWithPanels() {
		p.publishOne(ctx, id)
	}
}

func (p *AvailabilityPublisher) publishOne(ctx context.Context, parkingID uint64) {
	panel, _, err := p.availability.Get(ctx, parkingID)
	if err != nil {
		p.logger.WithError(err).WithField("parking_id", parkingID).Debug("Sin disponibilidad para publicar")
		return
	}
	payload, err := json.Marshal(panel)
	if err != nil {
		return
	}
	// Se compara sin la hora: si solo cambió last_updated no se vuelve a publicar
	withoutTime := *panel
	withoutTime.LastUpdated = ""
	fingerprint, _ := json.Marshal(withoutTime)

	p.mu.Lock()
	unchanged := p.last[parkingID] == string(fingerprint)
	p.mu.Unlock()
	if unchanged {
		return
	}
	if err := p.publisher.Publish(AvailabilityTopic(parkingID), payload, true); err != nil {
		p.logger.WithError(err).Warn("No se pudo publicar la disponibilidad")
		return
	}
	p.mu.Lock()
	p.last[parkingID] = string(fingerprint)
	p.mu.Unlock()
	p.logger.WithFields(logrus.Fields{"parking_id": parkingID, "message": panel.Message}).Info("Disponibilidad publicada")
}
