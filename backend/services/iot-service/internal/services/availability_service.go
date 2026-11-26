package services

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/redis/go-redis/v9"
)

// Estado del parqueo o zona. El panel muestra siempre total, disponibles y ocupados;
// el estado solo le dice al firmware si debe mostrar un aviso en lugar de los números.
const (
	PanelStateOpen       = "OPEN"         // atiende: se muestran los números
	PanelStateFull       = "FULL"         // atiende, sin espacios disponibles
	PanelStateClosed     = "CLOSED"       // no atiende a esta hora: aviso "CERRADO"
	PanelStateOutOfOrder = "OUT_OF_ORDER" // mantenimiento o fuera de servicio
)

// PanelAvailability lo que muestra el panel junto a un parqueo (solo ese parqueo), de arriba hacia abajo:
//
//	PARQUEO SAN MARTÍN     (parking_name, nombre completo)
//	TOTAL         5        (disponibles + ocupados; sin mantenimiento ni fuera de servicio)
//	DISPONIBLES   4
//	OCUPADOS      1
//
// Es el mismo contenido por HTTP y por MQTT.
type PanelAvailability struct {
	ParkingID       uint64 `json:"parking_id"`
	ParkingName     string `json:"parking_name"`
	TotalSpaces     int    `json:"total_spaces"`
	AvailableSpaces int    `json:"available_spaces"`
	OccupiedSpaces  int    `json:"occupied_spaces"`
	State           string `json:"state"`
	Notice          string `json:"notice,omitempty"` // "CERRADO" o "FUERA DE SERVICIO"; vacío si atiende
	LastUpdated     string `json:"last_updated"`
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

// Get lo que muestra el panel del parqueo y si vino de la caché
func (s *AvailabilityService) Get(ctx context.Context, parkingID uint64) (*PanelAvailability, bool, error) {
	availability, cacheHit, err := s.parkingAvailability(ctx, parkingID)
	if err != nil {
		return nil, false, err
	}
	return ToPanel(availability), cacheHit, nil
}

// parkingAvailability respuesta de parking-service, en caché unos segundos. Si Redis falla, consulta directo.
func (s *AvailabilityService) parkingAvailability(ctx context.Context, parkingID uint64) (*ParkingAvailability, bool, error) {
	if s.redis != nil {
		if cached, err := s.redis.Get(ctx, cacheKey(parkingID)).Result(); err == nil && cached != "" {
			var a ParkingAvailability
			if json.Unmarshal([]byte(cached), &a) == nil {
				return &a, true, nil
			}
		}
	}
	a, err := s.parking.GetAvailability(ctx, parkingID)
	if err != nil {
		return nil, false, err
	}
	if s.redis != nil {
		if data, err := json.Marshal(a); err == nil {
			s.redis.Set(ctx, cacheKey(parkingID), data, s.ttl)
		}
	}
	return a, false, nil
}

// Invalidate borra la caché de un parqueo
func (s *AvailabilityService) Invalidate(ctx context.Context, parkingID uint64) error {
	if s.redis == nil {
		return nil
	}
	// También el listado: los paneles de avenida lo usan
	return s.redis.Del(ctx, cacheKey(parkingID), publicListKey).Err()
}

// ToPanel traduce la respuesta de parking-service a lo que muestra el panel del parqueo
func ToPanel(a *ParkingAvailability) *PanelAvailability {
	state, notice := panelState(a.Operational, a.OpenNow, a.AvailableSpaces)
	return &PanelAvailability{
		ParkingID:       a.ParkingID,
		ParkingName:     a.Name,
		TotalSpaces:     a.UsableSpaces,
		AvailableSpaces: a.AvailableSpaces,
		OccupiedSpaces:  a.OccupiedSpaces,
		State:           state,
		Notice:          notice,
		LastUpdated:     a.GeneratedAt,
	}
}

func panelState(operational, openNow bool, available int) (string, string) {
	switch {
	case !operational:
		return PanelStateOutOfOrder, "FUERA DE SERVICIO"
	case !openNow:
		return PanelStateClosed, "CERRADO"
	case available == 0:
		return PanelStateFull, ""
	default:
		return PanelStateOpen, ""
	}
}

// ============================================================================
// PANELES DE AVENIDA (VARIOS PARQUEOS)
// ============================================================================

// AvenueItem un parqueo en la rotación del panel de avenida (una "pantalla" de 10 s)
type AvenueItem struct {
	ParkingID       uint64 `json:"parking_id"`
	ParkingName     string `json:"parking_name"`
	Address         string `json:"address,omitempty"`
	TotalSpaces     int    `json:"total_spaces"`
	AvailableSpaces int    `json:"available_spaces"`
	OccupiedSpaces  int    `json:"occupied_spaces"`
	State           string `json:"state"`
	Notice          string `json:"notice,omitempty"`
}

// AvenuePanel lo que recibe un panel de avenida. El firmware muestra los parqueos uno por uno,
// rotation_seconds cada uno, y vuelve a empezar; al llegar una lista nueva sigue con ella.
type AvenuePanel struct {
	DeviceID        string       `json:"device_id"`
	RotationSeconds int          `json:"rotation_seconds"`
	Parkings        []AvenueItem `json:"parkings"`
	LastUpdated     string       `json:"last_updated"`
}

const publicListKey = "availability:public-list"

// GetAvenue disponibilidad de los parqueos con una sola consulta a parking-service
// (listado público en caché, por nombre). ids vacío = todos; un ID que ya no se publica se omite.
func (s *AvailabilityService) GetAvenue(ctx context.Context, deviceID string, ids []uint64, rotationSeconds int) (*AvenuePanel, bool, error) {
	cards, cacheHit, err := s.publicList(ctx)
	if err != nil {
		return nil, false, err
	}
	byID := make(map[uint64]PublicParkingCard, len(cards))
	for _, c := range cards {
		byID[c.ID] = c
	}
	var selected []PublicParkingCard
	if len(ids) == 0 {
		selected = cards
	} else {
		for _, id := range ids {
			if c, ok := byID[id]; ok {
				selected = append(selected, c)
			}
		}
	}

	panel := &AvenuePanel{DeviceID: deviceID, RotationSeconds: rotationSeconds, Parkings: make([]AvenueItem, 0, len(selected)),
		LastUpdated: time.Now().Format(time.RFC3339)}
	for _, c := range selected {
		state, notice := panelState(c.Operational, c.OpenNow, c.AvailableSpaces)
		panel.Parkings = append(panel.Parkings, AvenueItem{ParkingID: c.ID, ParkingName: c.Name, Address: c.Address,
			TotalSpaces: c.UsableSpaces, AvailableSpaces: c.AvailableSpaces, OccupiedSpaces: c.OccupiedSpaces,
			State: state, Notice: notice})
	}
	return panel, cacheHit, nil
}

func (s *AvailabilityService) publicList(ctx context.Context) ([]PublicParkingCard, bool, error) {
	if s.redis != nil {
		if cached, err := s.redis.Get(ctx, publicListKey).Result(); err == nil && cached != "" {
			var cards []PublicParkingCard
			if json.Unmarshal([]byte(cached), &cards) == nil {
				return cards, true, nil
			}
		}
	}
	cards, err := s.parking.ListPublicParkings(ctx)
	if err != nil {
		return nil, false, err
	}
	if s.redis != nil {
		if data, err := json.Marshal(cards); err == nil {
			s.redis.Set(ctx, publicListKey, data, s.ttl)
		}
	}
	return cards, false, nil
}
