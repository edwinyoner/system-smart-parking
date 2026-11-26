package messaging

import "testing"

func TestDeviceFromSensorTopic(t *testing.T) {
	if id, err := DeviceFromSensorTopic("smartparking/sensors/SENSOR-A01/reading"); err != nil || id != "SENSOR-A01" {
		t.Fatalf("id %q err %v", id, err)
	}
	for _, bad := range []string{"smartparking/sensors//reading", "smartparking/sensors/X", "otro/sensors/X/reading",
		"smartparking/sensors/X/reading/extra"} {
		if _, err := DeviceFromSensorTopic(bad); err == nil {
			t.Errorf("%q debió rechazarse", bad)
		}
	}
}
