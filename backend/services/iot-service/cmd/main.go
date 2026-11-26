package main

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/edwinyoner/winner-systems-system-smart-parking/backend/services/iot-service/internal/api"
	"github.com/edwinyoner/winner-systems-system-smart-parking/backend/services/iot-service/internal/api/handlers"
	"github.com/edwinyoner/winner-systems-system-smart-parking/backend/services/iot-service/internal/config"
	"github.com/edwinyoner/winner-systems-system-smart-parking/backend/services/iot-service/internal/database"
	"github.com/edwinyoner/winner-systems-system-smart-parking/backend/services/iot-service/internal/messaging"
	"github.com/edwinyoner/winner-systems-system-smart-parking/backend/services/iot-service/internal/repository"
	"github.com/edwinyoner/winner-systems-system-smart-parking/backend/services/iot-service/internal/services"
	"github.com/sirupsen/logrus"
)

func main() {
	// ========================================================================
	// CONFIGURACIÓN INICIAL
	// ========================================================================

	// Cargar configuración
	cfg := config.LoadConfig()

	// Configurar logger
	logger := setupLogger(cfg.Logging.Level)
	logger.Info("Iniciando iot-service...")
	logger.WithFields(logrus.Fields{
		"environment": cfg.Server.GinMode,
		"port":        cfg.Server.Port,
		"version":     "1.0.0",
	}).Info("Configuración cargada")

	// ========================================================================
	// CONEXIONES A BASES DE DATOS
	// ========================================================================

	// Conectar a MySQL
	logger.Info("Conectando a MySQL...")
	if err := database.InitMySQL(cfg); err != nil {
		logger.Fatalf("Error al conectar a MySQL: %v", err)
	}
	db := database.GetDB()
	logger.Info("MySQL conectado exitosamente")

	// Conectar a Redis
	logger.Info("Conectando a Redis...")
	if err := database.InitRedis(cfg); err != nil {
		logger.Fatalf("Error al conectar a Redis: %v", err)
	}
	redisClient := database.GetRedis()

	// Verificar conexión Redis
	if err := redisClient.Ping(context.Background()).Err(); err != nil {
		logger.Fatalf("Error al hacer ping a Redis: %v", err)
	}
	logger.Info("Redis conectado exitosamente")

	// ========================================================================
	// INICIALIZAR REPOSITORIOS
	// ========================================================================

	logger.Info("Inicializando repositorios...")
	deviceRepo := repository.NewDeviceRepository(db)
	captureRepo := repository.NewCaptureRepository(db)
	// snapshotRepo := repository.NewSnapshotRepository(db)      // Comentado (no usado aún)
	// detectionRepo := repository.NewDetectionRepository(db)    // Comentado (no usado aún)
	displayLogRepo := repository.NewDisplayLogRepository(db)
	sensorReadingRepo := repository.NewSensorReadingRepository(db)
	logger.Info("Repositorios inicializados")

	// ========================================================================
	// INICIALIZAR SERVICIOS EXTERNOS
	// ========================================================================

	logger.Info("Inicializando clientes de servicios externos...")

	// Cliente ia-service
	iaClient := services.NewIaClient(cfg.Services.IaServiceURL)
	logger.WithField("url", cfg.Services.IaServiceURL).Info("ia-service client configurado")

	// Token de servicio de auth-service: con él iot-service llama a parking-service
	serviceTokens := services.NewServiceTokenProvider(
		cfg.Services.AuthServiceURL,
		cfg.Services.ServiceClientID,
		cfg.Services.ServiceClientSecret,
	)
	if cfg.Services.ServiceClientSecret == "" {
		logger.Warn("IOT_SERVICE_CLIENT_SECRET vacío: las capturas no podrán registrarse en parking-service")
	}

	// Cliente parking-service
	parkingClient := services.NewParkingClient(cfg.Services.ParkingServiceURL, serviceTokens)
	logger.WithField("url", cfg.Services.ParkingServiceURL).Info("parking-service client configurado")

	// Almacenamiento de fotos: MinIO en desarrollo, Google Cloud Storage en GCP
	imageStorage, err := services.NewImageStorage(context.Background(), services.StorageOptions{
		Provider:        cfg.Storage.Provider,
		SignedURLExpiry: time.Duration(cfg.Storage.SignedURLHours) * time.Hour,
		MinIO: services.S3Config{
			Endpoint:        cfg.S3.Endpoint,
			AccessKeyID:     cfg.S3.AccessKey,
			SecretAccessKey: cfg.S3.SecretKey,
			BucketName:      cfg.S3.BucketName,
			UseSSL:          cfg.S3.UseSSL,
		},
		GCS: services.GCSConfig{
			BucketName:      cfg.Storage.GCSBucketName,
			CredentialsFile: cfg.Storage.GCSCredentialsFile,
		},
	})
	if err != nil {
		logger.Fatalf("Error al inicializar el almacenamiento de fotos: %v", err)
	}
	if err := imageStorage.EnsureBucket(context.Background()); err != nil {
		logger.Warnf("Bucket de fotos no disponible (%s): %v", imageStorage.Provider(), err)
	} else {
		logger.WithField("provider", imageStorage.Provider()).Info("Bucket de fotos verificado")
	}

	// ========================================================================
	// INICIALIZAR HANDLERS
	// ========================================================================

	logger.Info("Inicializando handlers...")
	captureHandler := handlers.NewCaptureHandler(
		deviceRepo,
		captureRepo,
		iaClient,
		parkingClient,
		imageStorage,
		time.Duration(cfg.Services.CaptureTimeoutSeconds)*time.Second,
		logger,
	)

	// Disponibilidad para los paneles (HTTP y MQTT), con caché corta en Redis
	availabilityService := services.NewAvailabilityService(
		parkingClient,
		redisClient,
		time.Duration(cfg.Services.DisplayCacheSeconds)*time.Second,
	)

	displayHandler := handlers.NewDisplayHandler(
		deviceRepo,
		displayLogRepo,
		availabilityService,
		logger,
	)

	healthHandler := handlers.NewHealthHandler(
		db,
		redisClient,
		iaClient,
		parkingClient,
		logger,
	)
	logger.Info("Handlers inicializados")

	// ========================================================================
	// MQTT: SENSORES HC-SR04 Y PANELES LED
	// ========================================================================

	mqttCtx, stopMQTT := context.WithCancel(context.Background())
	defer stopMQTT()
	var mqttClient *messaging.Client
	if cfg.MQTT.Enabled {
		sensorService := services.NewSensorService(
			deviceRepo,
			sensorReadingRepo,
			parkingClient,
			services.NewSensorTracker(time.Duration(cfg.MQTT.SensorDebounceSeconds)*time.Second),
			services.SensorThresholds{MinCm: cfg.MQTT.SensorMinCm, OccupiedMaxCm: cfg.MQTT.SensorOccupiedMaxCm},
			logger,
		)
		mqttClient = messaging.NewClient(messaging.Config{
			BrokerURL: cfg.MQTT.BrokerURL,
			Username:  cfg.MQTT.Username,
			Password:  cfg.MQTT.Password,
			ClientID:  cfg.MQTT.ClientID,
		}, sensorService, logger)

		publisher := services.NewAvailabilityPublisher(
			deviceRepo,
			availabilityService,
			mqttClient,
			time.Duration(cfg.MQTT.AvailabilityPublishSeconds)*time.Second,
			logger,
		)
		sensorService.OnChange(publisher.Notify)
		// Tras (re)conectar, el broker pudo perder los mensajes retenidos: se republica todo
		mqttClient.OnConnect(publisher.Reset)

		mqttClient.Connect()
		go publisher.Run(mqttCtx)
		logger.WithField("broker", cfg.MQTT.BrokerURL).Info("MQTT iniciado (sensores y paneles)")
	} else {
		logger.Info("MQTT deshabilitado (MQTT_ENABLED=false): los paneles solo consultan por HTTP")
	}

	// ========================================================================
	// CONFIGURAR ROUTER
	// ========================================================================

	logger.Info("Configurando router...")
	router := api.SetupRouter(api.RouterConfig{
		CaptureHandler: captureHandler,
		DisplayHandler: displayHandler,
		HealthHandler:  healthHandler,
		RedisClient:    redisClient,
		Logger:         logger,
		Environment:    cfg.Server.GinMode,
	})
	logger.Info("Router configurado")

	// ========================================================================
	// INICIAR SERVIDOR HTTP
	// ========================================================================

	serverAddr := fmt.Sprintf(":%s", cfg.Server.Port)
	srv := &http.Server{
		Addr:        serverAddr,
		Handler:     router,
		ReadTimeout: 15 * time.Second,
		// Mayor que lo que la cámara espera el resultado; si no, se cortaría la respuesta 202
		WriteTimeout: time.Duration(cfg.Services.CaptureTimeoutSeconds+15) * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	// Canal para errores del servidor
	serverErrors := make(chan error, 1)

	// Iniciar servidor en goroutine
	go func() {
		logger.WithField("address", serverAddr).Info("Servidor HTTP iniciado")
		serverErrors <- srv.ListenAndServe()
	}()

	// ========================================================================
	// GRACEFUL SHUTDOWN
	// ========================================================================

	// Canal para señales del sistema
	shutdown := make(chan os.Signal, 1)
	signal.Notify(shutdown, os.Interrupt, syscall.SIGTERM)

	// Esperar señal de shutdown o error del servidor
	select {
	case err := <-serverErrors:
		logger.Fatalf("Error al iniciar servidor: %v", err)

	case sig := <-shutdown:
		logger.WithField("signal", sig.String()).Info("Señal de shutdown recibida")

		// Timeout para graceful shutdown
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()

		// Cerrar servidor HTTP
		logger.Info("Cerrando servidor HTTP...")
		if err := srv.Shutdown(ctx); err != nil {
			logger.Errorf("Error durante shutdown del servidor: %v", err)
			srv.Close()
		}

		// Cerrar MQTT
		stopMQTT()
		if mqttClient != nil {
			logger.Info("Cerrando conexión MQTT...")
			mqttClient.Disconnect()
		}

		// Cerrar conexión MySQL
		logger.Info("Cerrando conexión MySQL...")
		if err := database.CloseMySQL(); err != nil {
			logger.Errorf("Error al cerrar MySQL: %v", err)
		}

		// Cerrar conexión Redis
		logger.Info("Cerrando conexión Redis...")
		if err := database.CloseRedis(); err != nil {
			logger.Errorf("Error al cerrar Redis: %v", err)
		}

		logger.Info("Shutdown completado exitosamente")
	}
}

// setupLogger configura el logger según el level
func setupLogger(level string) *logrus.Logger {
	logger := logrus.New()

	// Configurar nivel de log
	switch level {
	case "debug":
		logger.SetLevel(logrus.DebugLevel)
	case "info":
		logger.SetLevel(logrus.InfoLevel)
	case "warn":
		logger.SetLevel(logrus.WarnLevel)
	case "error":
		logger.SetLevel(logrus.ErrorLevel)
	default:
		logger.SetLevel(logrus.InfoLevel)
	}

	// Configurar formato
	logger.SetFormatter(&logrus.TextFormatter{
		FullTimestamp:   true,
		TimestampFormat: "2006-01-02 15:04:05",
		ForceColors:     true,
	})

	logger.SetOutput(os.Stdout)

	return logger
}
