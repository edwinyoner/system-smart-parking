package services

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"
)

// San Martín: zona A con 3 libres y 2 ocupados; zona B con 1 libre, 1 ocupado y otros en mantenimiento
func sanMartin() ParkingAvailability {
	return ParkingAvailability{ParkingID: 1, Name: "Parqueo San Martín", Operational: true, OpenNow: true,
		TotalSpaces: 10, AvailableSpaces: 4, OccupiedSpaces: 3, UsableSpaces: 7,
		Zones: []ZoneAvailability{
			{ID: 10, Code: "A", Name: "Zona A", Operational: true, TotalSpaces: 5, AvailableSpaces: 3, OccupiedSpaces: 2, UsableSpaces: 5},
			{ID: 11, Code: "B", Name: "Zona B", Operational: true, TotalSpaces: 5, AvailableSpaces: 1, OccupiedSpaces: 1, UsableSpaces: 2},
		}}
}

func TestToPanel_soloEseParqueo(t *testing.T) {
	a := sanMartin()
	p := ToPanel(&a)
	if p.ParkingName != "Parqueo San Martín" {
		t.Fatalf("encabezado: %+v", p)
	}
	// Total = disponibles + ocupados: los de mantenimiento no se cuentan
	if p.TotalSpaces != 7 || p.AvailableSpaces != 4 || p.OccupiedSpaces != 3 || p.State != PanelStateOpen || p.Notice != "" {
		t.Fatalf("números: %+v", p)
	}
}

func TestToPanel_estados(t *testing.T) {
	cases := []struct {
		name   string
		change func(*ParkingAvailability)
		state  string
		notice string
	}{
		{"lleno", func(a *ParkingAvailability) { a.AvailableSpaces = 0 }, PanelStateFull, ""},
		{"fuera de horario", func(a *ParkingAvailability) { a.OpenNow, a.AvailableSpaces = false, 0 }, PanelStateClosed, "CERRADO"},
		{"mantenimiento", func(a *ParkingAvailability) { a.Operational = false }, PanelStateOutOfOrder, "FUERA DE SERVICIO"},
	}
	for _, tc := range cases {
		a := sanMartin()
		tc.change(&a)
		p := ToPanel(&a)
		if p.State != tc.state || p.Notice != tc.notice {
			t.Errorf("%s: %+v", tc.name, p)
		}
	}
}

func TestGetAvailability_rutaPublicaSinToken(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "" {
			t.Error("la disponibilidad es pública: no debe enviar token")
		}
		switch r.URL.Path {
		case "/public/parkings/1/availability":
			_, _ = w.Write([]byte(`{"parkingId":1,"name":"Parqueo San Martín","status":"ACTIVE","operational":true,"openNow":true,
				"totalSpaces":5,"availableSpaces":2,"occupiedSpaces":3,"usableSpaces":5,
				"zones":[{"id":10,"code":"A","name":"Zona A","operational":true,"availableSpaces":2,"occupiedSpaces":3,"usableSpaces":5}],
				"generatedAt":"2026-11-26T10:00:00"}`))
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	svc := NewAvailabilityService(NewParkingClient(srv.URL, nil), nil, 0)

	panel, cacheHit, err := svc.Get(context.Background(), 1)
	if err != nil || cacheHit || panel.TotalSpaces != 5 || panel.AvailableSpaces != 2 || panel.OccupiedSpaces != 3 {
		t.Fatalf("panel %+v, cache %v, err %v", panel, cacheHit, err)
	}
	if _, _, err := svc.Get(context.Background(), 9); !errors.Is(err, ErrParkingNotFound) {
		t.Fatalf("esperaba ErrParkingNotFound, llegó %v", err)
	}
}
