package services

import (
	"context"
	"errors"
	"fmt"
	"sync"
	"time"

	"github.com/edwinyoner/winner-systems-system-smart-parking/backend/services/iot-service/pkg/httpclient"
)

// renewBefore: el token se renueva un poco antes de vencer para no usar uno caducado en vuelo
const renewBefore = 30 * time.Second

// ErrServiceTokenDisabled indica que falta IOT_SERVICE_CLIENT_SECRET: sin él no hay token
var ErrServiceTokenDisabled = errors.New("falta IOT_SERVICE_CLIENT_SECRET: iot-service no puede autenticarse ante parking-service")

// ServiceTokenProvider obtiene de auth-service el token de servicio (client credentials)
// y lo guarda en memoria hasta poco antes de que venza. Es seguro usarlo desde varias
// goroutines: si dos capturas piden token a la vez, solo una llama a auth-service.
type ServiceTokenProvider struct {
	client       *httpclient.Client
	clientID     string
	clientSecret string
	now          func() time.Time

	mu        sync.Mutex
	token     string
	expiresAt time.Time
}

// NewServiceTokenProvider crea el proveedor. El secreto viene de la configuración, nunca del código.
func NewServiceTokenProvider(authURL, clientID, clientSecret string) *ServiceTokenProvider {
	return &ServiceTokenProvider{
		client: httpclient.NewClient(httpclient.Config{
			BaseURL:    authURL,
			Timeout:    5 * time.Second,
			MaxRetries: 2,
		}),
		clientID:     clientID,
		clientSecret: clientSecret,
		now:          time.Now,
	}
}

type serviceTokenRequest struct {
	ClientID     string `json:"clientId"`
	ClientSecret string `json:"clientSecret"`
}

type serviceTokenResponse struct {
	AccessToken string `json:"accessToken"`
	TokenType   string `json:"tokenType"`
	ExpiresIn   int64  `json:"expiresIn"` // segundos
}

// Token devuelve un token vigente, pidiéndolo a auth-service si no hay o está por vencer.
func (p *ServiceTokenProvider) Token(ctx context.Context) (string, error) {
	if p.clientSecret == "" {
		return "", ErrServiceTokenDisabled
	}

	p.mu.Lock()
	defer p.mu.Unlock()

	if p.token != "" && p.now().Add(renewBefore).Before(p.expiresAt) {
		return p.token, nil
	}

	resp, err := p.client.Post(ctx, "/service-token",
		serviceTokenRequest{ClientID: p.clientID, ClientSecret: p.clientSecret}, nil)
	if err != nil {
		return "", fmt.Errorf("auth-service no respondió al pedir el token de servicio: %w", err)
	}
	if !resp.IsSuccess() {
		// 401: ID o secreto incorrectos. No se registra el secreto en ningún mensaje.
		return "", fmt.Errorf("auth-service rechazó el token de servicio (HTTP %d)", resp.StatusCode)
	}

	var body serviceTokenResponse
	if err := resp.DecodeJSON(&body); err != nil || body.AccessToken == "" {
		return "", fmt.Errorf("respuesta de token de servicio inválida")
	}

	p.token = body.AccessToken
	p.expiresAt = p.now().Add(time.Duration(body.ExpiresIn) * time.Second)
	return p.token, nil
}

// Invalidate descarta el token guardado (por ejemplo, si parking-service respondió 401)
func (p *ServiceTokenProvider) Invalidate() {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.token = ""
	p.expiresAt = time.Time{}
}
