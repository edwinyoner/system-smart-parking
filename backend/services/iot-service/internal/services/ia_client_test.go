package services

import (
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestRecognizePlate_enviaArchivoYLeeLaRespuestaReal(t *testing.T) {
	image := []byte{0xFF, 0xD8, 0xFF, 0x01, 0x02}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/recognitions" {
			t.Errorf("ruta inesperada %s", r.URL.Path)
		}
		file, header, err := r.FormFile("image")
		if err != nil {
			t.Fatalf("ia-service espera el campo 'image' en multipart: %v", err)
		}
		got, _ := io.ReadAll(file)
		if r.FormValue("capture_id") != "cap-9" {
			t.Errorf("falta capture_id, llegó %q", r.FormValue("capture_id"))
		}
		if string(got) != string(image) || header.Filename != "CAM-1.jpg" {
			t.Errorf("archivo inesperado %q (%d bytes)", header.Filename, len(got))
		}
		_, _ = w.Write([]byte(`{"status":"SUCCESS","message":"ok","plate_number":"ABC-123",
			"detection":{"bbox":{"x":1,"y":1,"width":2,"height":2},"confidence":0.95},
			"ocr":{"text":"ABC-123","confidence":0.88},"processing_time_ms":850}`))
	}))
	defer srv.Close()

	resp, err := NewIaClient(srv.URL).RecognizePlate(context.Background(),
		PlateRecognitionRequest{Image: image, DeviceID: "CAM-1", CaptureID: "cap-9"})
	if err != nil {
		t.Fatal(err)
	}
	if resp.PlateNumber != "ABC-123" || resp.Confidence != 0.88 || resp.ProcessingMS != 850 {
		t.Fatalf("lectura inesperada: %+v", resp)
	}
}

func TestToPlateRecognition_sinPlacaConfianzaCero(t *testing.T) {
	got := toPlateRecognition(iaRecognitionResponse{Status: "NO_PLATE_DETECTED"})
	if got.PlateNumber != "" || got.Confidence != 0 {
		t.Fatalf("sin placa no debe haber confianza: %+v", got)
	}
}

func TestToPlateRecognition_usaElTextoDelOcrSiNoHayPlacaNormalizada(t *testing.T) {
	raw := iaRecognitionResponse{Status: "INVALID_FORMAT"}
	raw.OCR = &struct {
		Text       string  `json:"text"`
		Confidence float64 `json:"confidence"`
	}{Text: " AB1234 ", Confidence: 0.6}
	got := toPlateRecognition(raw)
	// parking-service decide si el formato vale; aquí solo se pasa lo leído
	if got.PlateNumber != "AB1234" || got.Confidence != 0.6 {
		t.Fatalf("lectura inesperada: %+v", got)
	}
}
