package services

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/edwinyoner/winner-systems-system-smart-parking/backend/services/iot-service/internal/models"
)

const publicList = `[
 {"id":1,"code":"PSM","name":"San Martín","status":"ACTIVE","operational":true,"openNow":true,"totalSpaces":50,"availableSpaces":12,"occupiedSpaces":36,"usableSpaces":48},
 {"id":2,"code":"PLB","name":"Las Banderas","status":"ACTIVE","operational":true,"openNow":true,"totalSpaces":30,"availableSpaces":0,"occupiedSpaces":30,"usableSpaces":30},
 {"id":3,"code":"PCE","name":"Centro","status":"MAINTENANCE","operational":false,"openNow":true,"totalSpaces":20,"availableSpaces":0,"occupiedSpaces":0,"usableSpaces":0}
]`

func avenueService(t *testing.T) (*AvailabilityService, *int) {
	calls := 0
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls++
		if r.URL.Path != "/public/parkings" || r.Header.Get("Authorization") != "" {
			t.Errorf("ruta %s o token inesperado", r.URL.Path)
		}
		_, _ = w.Write([]byte(publicList))
	}))
	t.Cleanup(srv.Close)
	return NewAvailabilityService(NewParkingClient(srv.URL, nil), nil, 0), &calls
}

func TestGetAvenue_respetaElOrdenYOmiteLosQueNoExisten(t *testing.T) {
	svc, calls := avenueService(t)

	panel, _, err := svc.GetAvenue(context.Background(), "DISPLAY-AV-01", []uint64{2, 9, 1}, 10)
	if err != nil {
		t.Fatal(err)
	}
	if *calls != 1 {
		t.Fatalf("debe ser una sola consulta, fueron %d", *calls)
	}
	if len(panel.Parkings) != 2 || panel.Parkings[0].ParkingName != "Las Banderas" || panel.Parkings[1].ParkingName != "San Martín" {
		t.Fatalf("orden o filtro incorrecto: %+v", panel.Parkings)
	}
	psm := panel.Parkings[1]
	if psm.TotalSpaces != 48 || psm.AvailableSpaces != 12 || psm.OccupiedSpaces != 36 || panel.Parkings[0].State != PanelStateFull {
		t.Fatalf("números: %+v", panel.Parkings)
	}
}

func TestGetAvenue_sinListaMuestraTodos(t *testing.T) {
	svc, _ := avenueService(t)
	panel, _, err := svc.GetAvenue(context.Background(), "DISPLAY-AV-01", nil, 10)
	if err == nil && panel.RotationSeconds != 10 {
		t.Fatal("la rotación viaja al panel")
	}
	if err != nil || len(panel.Parkings) != 3 || panel.Parkings[2].Notice != "FUERA DE SERVICIO" {
		t.Fatalf("%+v %v", panel, err)
	}
}

func TestDevice_panelDeAvenida(t *testing.T) {
	id := uint64(1)
	parkingPanel := models.Device{DeviceType: models.DeviceTypeESP32Display, ParkingID: &id}
	avenue := models.Device{DeviceType: models.DeviceTypeESP32Display, Metadata: `{"parking_ids":[2,1]}`}
	if parkingPanel.IsAvenuePanel() || !avenue.IsAvenuePanel() {
		t.Fatal("un panel sin parqueo es de avenida")
	}
	if ids := avenue.PanelParkingIDs(); len(ids) != 2 || ids[0] != 2 {
		t.Fatalf("ids %v", ids)
	}
	if avenue.PanelRotationSeconds() != models.DefaultRotationSeconds {
		t.Fatal("sin configurar, 10 s por parqueo")
	}
	custom := models.Device{Metadata: `{"rotation_seconds":15}`}
	tooFast := models.Device{Metadata: `{"rotation_seconds":1}`}
	if custom.PanelRotationSeconds() != 15 || tooFast.PanelRotationSeconds() != models.DefaultRotationSeconds {
		t.Fatal("rotación configurable entre 5 y 60 s")
	}
	broken := models.Device{Metadata: "no es json"}
	if ids := broken.PanelParkingIDs(); ids != nil {
		t.Fatal("metadata inválida = todos")
	}
}
