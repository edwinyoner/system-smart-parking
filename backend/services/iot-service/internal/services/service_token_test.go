package services

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"
)

// authStub simula POST /service-token de auth-service y cuenta las llamadas
func authStub(t *testing.T, calls *int32, expiresIn int64) *httptest.Server {
	t.Helper()
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/service-token" || r.Method != http.MethodPost {
			t.Errorf("ruta inesperada %s %s", r.Method, r.URL.Path)
		}
		var body serviceTokenRequest
		_ = json.NewDecoder(r.Body).Decode(&body)
		if body.ClientID != "iot-service" || body.ClientSecret != "secreto-de-prueba" {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		n := atomic.AddInt32(calls, 1)
		_ = json.NewEncoder(w).Encode(serviceTokenResponse{
			AccessToken: "token-" + string(rune('0'+n)), TokenType: "Bearer", ExpiresIn: expiresIn,
		})
	}))
}

func TestToken_seGuardaHastaPocoAntesDeVencer(t *testing.T) {
	var calls int32
	srv := authStub(t, &calls, 300)
	defer srv.Close()

	p := NewServiceTokenProvider(srv.URL, "iot-service", "secreto-de-prueba")
	now := time.Now()
	p.now = func() time.Time { return now }

	first, err := p.Token(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	second, _ := p.Token(context.Background())
	if first != second || calls != 1 {
		t.Fatalf("debió reutilizar el token: %q %q, llamadas %d", first, second, calls)
	}

	// A 20 s de vencer (menos que el margen de 30 s) se pide otro
	now = now.Add(280 * time.Second)
	third, _ := p.Token(context.Background())
	if third == first || calls != 2 {
		t.Fatalf("debió renovar el token, llamadas %d", calls)
	}
}

func TestToken_sinSecretoNoLlamaAAuth(t *testing.T) {
	p := NewServiceTokenProvider("http://127.0.0.1:1", "iot-service", "")
	if _, err := p.Token(context.Background()); err != ErrServiceTokenDisabled {
		t.Fatalf("esperaba ErrServiceTokenDisabled, llegó %v", err)
	}
}

func TestToken_credencialIncorrecta(t *testing.T) {
	var calls int32
	srv := authStub(t, &calls, 300)
	defer srv.Close()

	p := NewServiceTokenProvider(srv.URL, "iot-service", "otro")
	if _, err := p.Token(context.Background()); err == nil {
		t.Fatal("esperaba error con un secreto incorrecto")
	}
}

func TestInvalidate_obligaAPedirOtro(t *testing.T) {
	var calls int32
	srv := authStub(t, &calls, 300)
	defer srv.Close()

	p := NewServiceTokenProvider(srv.URL, "iot-service", "secreto-de-prueba")
	_, _ = p.Token(context.Background())
	p.Invalidate()
	_, _ = p.Token(context.Background())
	if calls != 2 {
		t.Fatalf("esperaba 2 llamadas, hubo %d", calls)
	}
}
