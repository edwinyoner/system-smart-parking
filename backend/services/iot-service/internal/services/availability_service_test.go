package services

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestToPanel_estados(t *testing.T) {
	base := ParkingAvailability{ParkingID: 1, Name: "San Martín", Operational: true, OpenNow: true, TotalSpaces: 10, AvailableSpaces: 4,
		Zones: []ZoneAvailability{{Code: "A", AvailableSpaces: 3}, {Code: "B", AvailableSpaces: 1}}}

	cases := []struct {
		name     string
		change   func(*ParkingAvailability)
		state    string
		message  string
		occupied int
	}{
		{"con libres", func(a *ParkingAvailability) {}, PanelStateOpen, "LIBRES 4", 6},
		{"lleno", func(a *ParkingAvailability) { a.AvailableSpaces = 0 }, PanelStateFull, "LLENO", 10},
		{"fuera de horario", func(a *ParkingAvailability) { a.OpenNow, a.AvailableSpaces = false, 0 }, PanelStateClosed, "CERRADO", 10},
		{"mantenimiento", func(a *ParkingAvailability) { a.Operational = false }, PanelStateOutOfOrder, "FUERA DE SERVICIO", 6},
	}
	for _, tc := range cases {
		a := base
		tc.change(&a)
		p := ToPanel(&a)
		if p.State != tc.state || p.Message != tc.message || p.OccupiedSpaces != tc.occupied {
			t.Errorf("%s: %+v", tc.name, p)
		}
	}
	if p := ToPanel(&base); len(p.Zones) != 2 || p.Zones[0].Code != "A" || p.Zones[0].Available != 3 {
		t.Errorf("zonas inesperadas: %+v", p.Zones)
	}
}

func TestGetAvailability_rutaPublicaSinToken(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "" {
			t.Error("la disponibilidad es pública: no debe enviar token")
		}
		switch r.URL.Path {
		case "/public/parkings/1/availability":
			_, _ = w.Write([]byte(`{"parkingId":1,"name":"San Martín","status":"ACTIVE","operational":true,"openNow":true,
				"totalSpaces":5,"availableSpaces":2,"zones":[{"code":"A","availableSpaces":2}],"generatedAt":"2026-11-26T10:00:00"}`))
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	svc := NewAvailabilityService(NewParkingClient(srv.URL, nil), nil, 0)

	panel, cacheHit, err := svc.Get(context.Background(), 1)
	if err != nil || cacheHit || panel.Message != "LIBRES 2" || panel.TotalSpaces != 5 {
		t.Fatalf("panel %+v, cache %v, err %v", panel, cacheHit, err)
	}
	if _, _, err := svc.Get(context.Background(), 9); !errors.Is(err, ErrParkingNotFound) {
		t.Fatalf("esperaba ErrParkingNotFound, llegó %v", err)
	}
}
