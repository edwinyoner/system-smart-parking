package services

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
)

func TestProcessCapture_enviaTokenYLeeLaDecision(t *testing.T) {
	var authCalls int32
	auth := authStub(t, &authCalls, 300)
	defer auth.Close()

	zone := uint64(3)
	parking := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/internal/captures" {
			t.Errorf("ruta inesperada %s", r.URL.Path)
		}
		if !strings.HasPrefix(r.Header.Get("Authorization"), "Bearer token-") {
			t.Errorf("falta el token de servicio")
		}
		var req CameraCaptureRequest
		_ = json.NewDecoder(r.Body).Decode(&req)
		if req.Direction != "ENTRY" || req.ZoneID == nil || *req.ZoneID != 3 || req.Confidence != 0.91 {
			t.Errorf("petición inesperada: %+v", req)
		}
		_, _ = w.Write([]byte(`{"outcome":"ENTRY_REGISTERED","openGate":true,"message":"Bienvenido",
			"plateNumber":"ABC-123","transactionId":42,"reviewId":null,"spaceCode":"A-01"}`))
	}))
	defer parking.Close()

	client := NewParkingClient(parking.URL, NewServiceTokenProvider(auth.URL, "iot-service", "secreto-de-prueba"))
	result, err := client.ProcessCapture(context.Background(), CameraCaptureRequest{
		CaptureID: "c-1", Direction: "ENTRY", ParkingID: 1, ZoneID: &zone,
		DeviceID: "CAM-1", PlateNumber: "ABC123", Confidence: 0.91,
	})
	if err != nil {
		t.Fatal(err)
	}
	if result.Outcome != OutcomeEntryRegistered || !result.OpenGate || result.TransactionID == nil || *result.TransactionID != 42 {
		t.Fatalf("resultado inesperado: %+v", result)
	}
}

func TestProcessCapture_con401RenuevaElTokenUnaVez(t *testing.T) {
	var authCalls, parkingCalls int32
	auth := authStub(t, &authCalls, 300)
	defer auth.Close()

	parking := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if atomic.AddInt32(&parkingCalls, 1) == 1 {
			w.WriteHeader(http.StatusUnauthorized) // token rotado o vencido
			return
		}
		if r.Header.Get("Authorization") != "Bearer token-2" {
			t.Errorf("debió llegar el token nuevo, llegó %q", r.Header.Get("Authorization"))
		}
		_, _ = w.Write([]byte(`{"outcome":"PENDING_REVIEW","openGate":false,"reviewId":7}`))
	}))
	defer parking.Close()

	client := NewParkingClient(parking.URL, NewServiceTokenProvider(auth.URL, "iot-service", "secreto-de-prueba"))
	result, err := client.ProcessCapture(context.Background(), CameraCaptureRequest{CaptureID: "c-2", Direction: "EXIT"})
	if err != nil {
		t.Fatal(err)
	}
	if result.Outcome != OutcomePendingReview || result.OpenGate || authCalls != 2 {
		t.Fatalf("resultado %+v, llamadas a auth %d", result, authCalls)
	}
}

func TestProcessCapture_errorDeParkingNoAbreLaBarrera(t *testing.T) {
	var authCalls int32
	auth := authStub(t, &authCalls, 300)
	defer auth.Close()
	parking := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusBadRequest)
	}))
	defer parking.Close()

	client := NewParkingClient(parking.URL, NewServiceTokenProvider(auth.URL, "iot-service", "secreto-de-prueba"))
	if _, err := client.ProcessCapture(context.Background(), CameraCaptureRequest{CaptureID: "c-3"}); err == nil {
		t.Fatal("esperaba error con HTTP 400")
	}
}
