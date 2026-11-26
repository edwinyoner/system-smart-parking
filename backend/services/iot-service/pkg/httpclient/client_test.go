package httpclient

import (
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"
)

// Antes el cuerpo se leía una sola vez y los reintentos llegaban vacíos
func TestPost_reintentoReenviaElCuerpo(t *testing.T) {
	var calls int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		if string(body) != `{"a":1}` {
			t.Errorf("intento %d llegó con cuerpo %q", calls+1, body)
		}
		if atomic.AddInt32(&calls, 1) == 1 {
			w.WriteHeader(http.StatusServiceUnavailable)
			return
		}
		w.WriteHeader(http.StatusOK)
	}))
	defer srv.Close()

	c := NewClient(Config{BaseURL: srv.URL, Timeout: 2 * time.Second, MaxRetries: 1})
	resp, err := c.Post(context.Background(), "/x", map[string]int{"a": 1}, nil)
	if err != nil || resp.StatusCode != http.StatusOK || calls != 2 {
		t.Fatalf("resp %v, err %v, llamadas %d", resp, err, calls)
	}
}

func TestPostRaw_respetaElContentType(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Content-Type") != "multipart/form-data; boundary=x" {
			t.Errorf("Content-Type %q", r.Header.Get("Content-Type"))
		}
	}))
	defer srv.Close()

	c := NewClient(Config{BaseURL: srv.URL, MaxRetries: 1})
	if _, err := c.PostRaw(context.Background(), "/x", []byte("--x--"), "multipart/form-data; boundary=x", nil); err != nil {
		t.Fatal(err)
	}
}
