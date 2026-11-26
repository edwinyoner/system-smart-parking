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

// PanelTopic tema del que lee un panel según cómo está registrado
func PanelTopic(d models.Device) string {
	if d.IsAvenuePanel() {
		return AvenueTopic(d.DeviceID)
	}
	return AvailabilityTopic(*d.ParkingID)
}

// AvenueTopic tema propio de cada panel de avenida (cada uno muestra su lista de parqueos)
func AvenueTopic(deviceID string) string {
	return fmt.Sprintf("smartparking/panels/%s/availability", deviceID)
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
	last map[string]string // último contenido publicado por tema (sin la hora)
	wake chan uint64
}

// NewAvailabilityPublisher crea el publicador
func NewAvailabilityPublisher(devices *repository.DeviceRepository, availability *AvailabilityService,
	publisher Publisher, interval time.Duration, logger *logrus.Logger) *AvailabilityPublisher {
	if interval <= 0 {
		interval = 5 * time.Second
	}
	return &AvailabilityPublisher{devices: devices, availability: availability, publisher: publisher,
		interval: interval, logger: logger, last: make(map[string]string), wake: make(chan uint64, 32)}
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
			p.publishAll(ctx)
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
	p.last = make(map[string]string)
}

// activePanels paneles activos (de parqueo y de avenida)
func (p *AvailabilityPublisher) activePanels() []models.Device {
	if p.devices == nil {
		return nil
	}
	panels, err := p.devices.FindByType(models.DeviceTypeESP32Display)
	if err != nil {
		p.logger.WithError(err).Warn("No se pudieron leer los paneles")
		return nil
	}
	active := panels[:0]
	for _, d := range panels {
		if d.IsActive() {
			active = append(active, d)
		}
	}
	return active
}

func (p *AvailabilityPublisher) publishAll(ctx context.Context) {
	published := make(map[string]bool) // varios paneles pueden compartir tema
	for _, d := range p.activePanels() {
		topic := PanelTopic(d)
		if published[topic] {
			continue
		}
		published[topic] = true
		if d.IsAvenuePanel() {
			p.publishAvenue(ctx, d)
		} else {
			p.publishPanel(ctx, topic, *d.ParkingID)
		}
	}
}

// publishPanel panel junto a un parqueo
func (p *AvailabilityPublisher) publishPanel(ctx context.Context, topic string, parkingID uint64) {
	panel, _, err := p.availability.Get(ctx, parkingID)
	if err != nil {
		p.logger.WithError(err).WithField("parking_id", parkingID).Debug("Sin disponibilidad para publicar")
		return
	}
	// Se compara sin la hora: si solo cambió last_updated no se vuelve a publicar
	withoutTime := *panel
	withoutTime.LastUpdated = ""
	p.publishIfChanged(topic, panel, withoutTime)
}

// publishAvenue panel de avenida (lista de parqueos, una consulta en caché para todos)
func (p *AvailabilityPublisher) publishAvenue(ctx context.Context, d models.Device) {
	panel, _, err := p.availability.GetAvenue(ctx, d.DeviceID, d.PanelParkingIDs(), d.PanelRotationSeconds())
	if err != nil {
		p.logger.WithError(err).Debug("Sin datos para el panel de avenida")
		return
	}
	withoutTime := *panel
	withoutTime.LastUpdated = ""
	p.publishIfChanged(AvenueTopic(d.DeviceID), panel, withoutTime)
}

// publishIfChanged publica (retenido) solo si el contenido sin la hora cambió
func (p *AvailabilityPublisher) publishIfChanged(topic string, value, withoutTime any) {
	payload, err := json.Marshal(value)
	if err != nil {
		return
	}
	fingerprint, _ := json.Marshal(withoutTime)

	p.mu.Lock()
	unchanged := p.last[topic] == string(fingerprint)
	p.mu.Unlock()
	if unchanged {
		return
	}
	if err := p.publisher.Publish(topic, payload, true); err != nil {
		p.logger.WithError(err).Warn("No se pudo publicar la disponibilidad")
		return
	}
	p.mu.Lock()
	p.last[topic] = string(fingerprint)
	p.mu.Unlock()
	p.logger.WithField("topic", topic).Info("Disponibilidad publicada")
}
