// Package messaging es la entrada MQTT de iot-service (equivale a los handlers HTTP):
// recibe los mensajes del broker y los pasa a los servicios.
//
// Temas:
//   - smartparking/sensors/{deviceId}/reading       (sensor -> iot-service), QoS 1
//   - smartparking/parkings/{parkingId}/availability (iot-service -> paneles), QoS 1, retenido
//
// Las ESP32-CAM no usan MQTT: envían la foto por HTTP y esperan la respuesta de la barrera.
package messaging

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"

	mqtt "github.com/eclipse/paho.mqtt.golang"
	"github.com/edwinyoner/winner-systems-system-smart-parking/backend/services/iot-service/internal/services"
	"github.com/sirupsen/logrus"
)

// SensorTopic suscripción a las lecturas de todos los sensores
const SensorTopic = "smartparking/sensors/+/reading"

const qos = 1

// Config conexión al broker
type Config struct {
	BrokerURL string
	Username  string
	Password  string
	ClientID  string
}

// Client cliente MQTT de iot-service: se suscribe a los sensores y publica a los paneles
type Client struct {
	client  mqtt.Client
	sensors *services.SensorService
	logger  *logrus.Logger
	// onConnect se llama en cada conexión (incluida la reconexión)
	onConnect func()
}

// NewClient prepara el cliente; Connect lo conecta. Se reconecta solo si el broker cae.
func NewClient(cfg Config, sensors *services.SensorService, logger *logrus.Logger) *Client {
	c := &Client{sensors: sensors, logger: logger}

	opts := mqtt.NewClientOptions().
		AddBroker(cfg.BrokerURL).
		SetClientID(cfg.ClientID).
		SetUsername(cfg.Username).
		SetPassword(cfg.Password).
		SetCleanSession(true).
		SetAutoReconnect(true).
		SetConnectRetry(true).
		SetConnectRetryInterval(5 * time.Second).
		SetMaxReconnectInterval(30 * time.Second).
		SetKeepAlive(30 * time.Second).
		SetOrderMatters(false)

	opts.SetOnConnectHandler(func(client mqtt.Client) {
		// Con sesión limpia la suscripción se pierde al reconectar: se vuelve a pedir
		token := client.Subscribe(SensorTopic, qos, c.handleSensor)
		if token.WaitTimeout(10*time.Second) && token.Error() == nil {
			logger.WithField("topic", SensorTopic).Info("MQTT conectado y suscrito")
		} else {
			logger.WithError(token.Error()).Error("MQTT: no se pudo suscribir a los sensores")
		}
		if c.onConnect != nil {
			c.onConnect()
		}
	})
	opts.SetConnectionLostHandler(func(_ mqtt.Client, err error) {
		logger.WithError(err).Warn("MQTT: conexión perdida, reintentando")
	})

	c.client = mqtt.NewClient(opts)
	return c
}

// OnConnect registra una acción para cada (re)conexión
func (c *Client) OnConnect(fn func()) {
	c.onConnect = fn
}

// Connect inicia la conexión. Con SetConnectRetry no falla si el broker aún no está:
// sigue intentando en segundo plano.
func (c *Client) Connect() {
	c.client.Connect()
}

// Publish implementa services.Publisher
func (c *Client) Publish(topic string, payload []byte, retained bool) error {
	if !c.client.IsConnectionOpen() {
		return errors.New("MQTT sin conexión")
	}
	token := c.client.Publish(topic, qos, retained, payload)
	if !token.WaitTimeout(5 * time.Second) {
		return errors.New("MQTT: tiempo de espera agotado al publicar")
	}
	return token.Error()
}

// IsConnected para el health check
func (c *Client) IsConnected() bool {
	return c.client.IsConnectionOpen()
}

// Disconnect cierra la conexión al apagar el servicio
func (c *Client) Disconnect() {
	c.client.Disconnect(1000)
}

// handleSensor procesa smartparking/sensors/{deviceId}/reading
func (c *Client) handleSensor(_ mqtt.Client, msg mqtt.Message) {
	deviceID, err := DeviceFromSensorTopic(msg.Topic())
	if err != nil {
		c.logger.WithField("topic", msg.Topic()).Warn("MQTT: tema de sensor inválido")
		return
	}
	var payload services.SensorPayload
	if err := json.Unmarshal(msg.Payload(), &payload); err != nil {
		c.logger.WithField("sensor", deviceID).Warn("MQTT: lectura con formato inválido")
		return
	}

	// La llamada a parking-service no debe bloquear la recepción de otros mensajes
	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
		defer cancel()
		if err := c.sensors.HandleReading(ctx, deviceID, payload); err != nil &&
			!errors.Is(err, services.ErrInvalidDistance) {
			c.logger.WithError(err).WithField("sensor", deviceID).Warn("Lectura de sensor no procesada")
		}
	}()
}

// DeviceFromSensorTopic extrae el deviceId de smartparking/sensors/{deviceId}/reading
func DeviceFromSensorTopic(topic string) (string, error) {
	parts := strings.Split(topic, "/")
	if len(parts) != 4 || parts[0] != "smartparking" || parts[1] != "sensors" || parts[3] != "reading" || parts[2] == "" {
		return "", fmt.Errorf("tema inválido: %s", topic)
	}
	return parts[2], nil
}
