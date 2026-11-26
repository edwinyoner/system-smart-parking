package services

import (
	"context"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"

	"github.com/sirupsen/logrus"
)

type fakePublisher struct {
	topics   []string
	retained []bool
}

func (f *fakePublisher) Publish(topic string, payload []byte, retained bool) error {
	f.topics = append(f.topics, topic)
	f.retained = append(f.retained, retained)
	return nil
}

func TestPublishOne_soloPublicaSiCambia(t *testing.T) {
	var available atomic.Int32
	available.Store(3)
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// La hora cambia en cada respuesta: no debe provocar una nueva publicación
		_, _ = w.Write([]byte(`{"parkingId":1,"name":"PSM","operational":true,"openNow":true,"totalSpaces":5,` +
			`"availableSpaces":` + string(rune('0'+available.Load())) + `,"zones":[],"generatedAt":"` + r.URL.RawQuery + `x"}`))
	}))
	defer srv.Close()

	pub := &fakePublisher{}
	p := NewAvailabilityPublisher(nil, NewAvailabilityService(NewParkingClient(srv.URL, nil), nil, 0), pub, 0, logrus.New())

	p.publishOne(context.Background(), 1)
	p.publishOne(context.Background(), 1)
	if len(pub.topics) != 1 || pub.topics[0] != "smartparking/parkings/1/availability" || !pub.retained[0] {
		t.Fatalf("debió publicar una vez, retenido: %v %v", pub.topics, pub.retained)
	}

	available.Store(2)
	p.publishOne(context.Background(), 1)
	if len(pub.topics) != 2 {
		t.Fatal("un cambio de libres debe publicarse")
	}

	p.Reset()
	p.publishOne(context.Background(), 1)
	if len(pub.topics) != 3 {
		t.Fatal("tras reconectar se republica")
	}
}
