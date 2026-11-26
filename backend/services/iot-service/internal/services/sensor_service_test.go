package services

import (
	"testing"
	"time"
)

func TestThresholds(t *testing.T) {
	th := SensorThresholds{MinCm: 3, OccupiedMaxCm: 80}
	cases := map[float64]bool{40: true, 80: true, 81: false, 250: false}
	for d, want := range cases {
		got, err := th.Occupied(d)
		if err != nil || got != want {
			t.Errorf("%v cm -> %v (%v), esperaba %v", d, got, err, want)
		}
	}
	for _, d := range []float64{0, 2, 500} {
		if _, err := th.Occupied(d); err != ErrInvalidDistance {
			t.Errorf("%v cm debió ser inválida", d)
		}
	}
}

func TestTracker_confirmaSoloSiSeMantiene(t *testing.T) {
	tr := NewSensorTracker(5 * time.Second)
	t0 := time.Date(2026, 11, 26, 10, 0, 0, 0, time.UTC)

	if _, ch := tr.Observe("S1", true, t0); ch {
		t.Fatal("la primera lectura no confirma nada")
	}
	if _, ch := tr.Observe("S1", true, t0.Add(3*time.Second)); ch {
		t.Fatal("a los 3 s aún no se confirma")
	}
	if st, ch := tr.Observe("S1", true, t0.Add(5*time.Second)); !ch || !st {
		t.Fatal("a los 5 s se confirma ocupado")
	}
	if _, ch := tr.Observe("S1", true, t0.Add(9*time.Second)); ch {
		t.Fatal("el mismo estado no se vuelve a avisar")
	}
}

func TestTracker_unaPersonaQuePasaNoCambiaElEstado(t *testing.T) {
	tr := NewSensorTracker(5 * time.Second)
	t0 := time.Date(2026, 11, 26, 10, 0, 0, 0, time.UTC)
	tr.Observe("S1", false, t0)
	tr.Observe("S1", false, t0.Add(5*time.Second)) // confirmado libre

	tr.Observe("S1", true, t0.Add(6*time.Second)) // alguien pasa por encima
	if _, ch := tr.Observe("S1", false, t0.Add(8*time.Second)); ch {
		t.Fatal("volver al estado confirmado no es un cambio")
	}
	// Si vuelve a aparecer, el conteo empieza de cero
	tr.Observe("S1", true, t0.Add(9*time.Second))
	if _, ch := tr.Observe("S1", true, t0.Add(12*time.Second)); ch {
		t.Fatal("el conteo debió reiniciarse")
	}
}

func TestTracker_forgetReenvia(t *testing.T) {
	tr := NewSensorTracker(0)
	t0 := time.Now()
	if _, ch := tr.Observe("S1", true, t0); !ch {
		t.Fatal("sin espera, confirma al instante")
	}
	tr.Forget("S1")
	if _, ch := tr.Observe("S1", true, t0); !ch {
		t.Fatal("tras Forget el estado se vuelve a enviar")
	}
}
