package handlers

import (
	"testing"

	"github.com/edwinyoner/winner-systems-system-smart-parking/backend/services/iot-service/internal/models"
)

// La columna MySQL es ENUM('ENTRY','EXIT'): el ESP32 puede enviar minúsculas
func TestNormalizeDirection(t *testing.T) {
	cases := map[string]models.CaptureType{
		"entry": models.CaptureTypeEntry, " ENTRY ": models.CaptureTypeEntry,
		"exit": models.CaptureTypeExit, "Exit": models.CaptureTypeExit,
	}
	for in, want := range cases {
		got, ok := normalizeDirection(in)
		if !ok || got != want {
			t.Errorf("%q -> %q (%v), esperaba %q", in, got, ok, want)
		}
	}
	if _, ok := normalizeDirection("salida"); ok {
		t.Error("un valor desconocido no debe aceptarse")
	}
}
