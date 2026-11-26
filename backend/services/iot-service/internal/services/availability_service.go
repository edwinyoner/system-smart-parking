package services

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/redis/go-redis/v9"
)

// Textos cortos para la pantalla del panel (16x2 o matriz LED)
const (
	PanelStateOpen       = "OPEN"
	PanelStateFull       = "FULL"
	PanelStateClosed     = "CLOSED"       // no atiende a esta hora
	PanelStateOutOfOrder = "OUT_OF_ORDER" // mantenimiento o fuera de servicio
)

// PanelZone disponibilidad de una zona en el formato del panel
type PanelZone struct {
	Code      string `json:"code"`
	Available int    `json:"available"`
}

// PanelAvailability lo que muestra un panel LED. Es el mismo contenido por HTTP y por MQTT.
type PanelAvailability struct {
	ParkingID       uint64      `json:"parking_id"`
	Name            string      `json:"name"`
	State           string      `json:"state"`
	Message         string      `json:"message"`
	TotalSpaces     int         `json:"total_spaces"`
	AvailableSpaces int         `json:"available_spaces"`
	OccupiedSpaces  int         `json:"occupied_spaces"`
	Zones           []PanelZone `json:"zones"`
	LastUpdated     string      `json:"last_updated"`
}

// AvailabilityService obtiene la disponibilidad de parking-service y la guarda unos
// segundos en Redis: muchos paneles del mismo parqueo hacen una sola consulta.
type AvailabilityService struct {
	parking *ParkingClient
	redis   *redis.Client
	ttl     time.Duration
}

// NewAvailabilityService crea el servicio. ttl debe ser menor que el intervalo de los paneles (10 s).
func NewAvailabilityService(parking *ParkingClient, redisClient *redis.Client, ttl time.Duration) *AvailabilityService {
	if ttl <= 0 {
		ttl = 5 * time.Second
	}
	return &AvailabilityService{parking: parking, redis: redisClient, ttl: ttl}
}

func cacheKey(parkingID uint64) string {
	return fmt.Sprintf("availability:parking:%d", parkingID)
}

// Get devuelve la disponibilidad y si vino de la caché. Si Redis falla, consulta directo.
func (s *AvailabilityService) Get(ctx context.Context, parkingID uint64) (*PanelAvailability, bool, error) {
	if s.redis != nil {
		if cached, err := s.redis.Get(ctx, cacheKey(parkingID)).Result(); err == nil && cached != "" {
			var panel PanelAvailability
			if json.Unmarshal([]byte(cached), &panel) == nil {
				return &panel, true, nil
			}
		}
	}

	availability, err := s.parking.GetAvailability(ctx, parkingID)
	if err != nil {
		return nil, false, err
	}
	panel := ToPanel(availability)

	if s.redis != nil {
		if data, err := json.Marshal(panel); err == nil {
			s.redis.Set(ctx, cacheKey(parkingID), data, s.ttl)
		}
	}
	return panel, false, nil
}

// Invalidate borra la caché de un parqueo
func (s *AvailabilityService) Invalidate(ctx context.Context, parkingID uint64) error {
	if s.redis == nil {
		return nil
	}
	return s.redis.Del(ctx, cacheKey(parkingID)).Err()
}

// ToPanel traduce la respuesta de parking-service a lo que muestra el panel
func ToPanel(a *ParkingAvailability) *PanelAvailability {
	panel := &PanelAvailability{
		ParkingID:       a.ParkingID,
		Name:            a.Name,
		TotalSpaces:     a.TotalSpaces,
		AvailableSpaces: a.AvailableSpaces,
		OccupiedSpaces:  max(a.TotalSpaces-a.AvailableSpaces, 0),
		LastUpdated:     a.GeneratedAt,
		Zones:           make([]PanelZone, 0, len(a.Zones)),
	}
	for _, z := range a.Zones {
		panel.Zones = append(panel.Zones, PanelZone{Code: z.Code, Available: z.AvailableSpaces})
	}

	switch {
	case !a.Operational:
		panel.State, panel.Message = PanelStateOutOfOrder, "FUERA DE SERVICIO"
	case !a.OpenNow:
		panel.State, panel.Message = PanelStateClosed, "CERRADO"
	case a.AvailableSpaces == 0:
		panel.State, panel.Message = PanelStateFull, "LLENO"
	default:
		panel.State, panel.Message = PanelStateOpen, fmt.Sprintf("LIBRES %d", a.AvailableSpaces)
	}
	return panel
}
