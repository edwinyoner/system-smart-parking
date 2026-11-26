package config

import (
	"log"
	"os"
	"strconv"

	"github.com/joho/godotenv"
)

// Config holds all configuration for the application
type Config struct {
	Server   ServerConfig
	MySQL    MySQLConfig
	Redis    RedisConfig
	Storage  StorageConfig
	S3       S3Config
	Eureka   EurekaConfig
	Services ServicesConfig
	MQTT     MQTTConfig
	RabbitMQ RabbitMQConfig
	Logging  LoggingConfig
}

type ServerConfig struct {
	Port    string
	Host    string
	GinMode string
}

type MySQLConfig struct {
	Host         string
	Port         string
	User         string
	Password     string
	Database     string
	MaxOpenConns int
	MaxIdleConns int
}

type RedisConfig struct {
	Host     string
	Port     string
	Password string
	DB       int
	PoolSize int
}

// StorageConfig dónde se guardan las fotos: minio (desarrollo) o gcs (GCP)
type StorageConfig struct {
	Provider           string
	SignedURLHours     int
	GCSBucketName      string
	GCSCredentialsFile string
}

type S3Config struct {
	Endpoint   string
	AccessKey  string
	SecretKey  string
	BucketName string
	UseSSL     bool
}

type EurekaConfig struct {
	ServerURL         string
	ServiceName       string
	ServiceInstanceID string
	ServiceIPAddress  string
}

type ServicesConfig struct {
	IaServiceURL      string
	ParkingServiceURL string
	// auth-service emite el token de servicio con el que iot-service llama a parking-service
	AuthServiceURL      string
	ServiceClientID     string
	ServiceClientSecret string
	// Tiempo máximo que el ESP32 espera el resultado de una captura (IA + parking)
	CaptureTimeoutSeconds int
	// Segundos que la disponibilidad queda en Redis (menos que los 10 s de polling del panel)
	DisplayCacheSeconds int
}

// MQTTConfig broker Mosquitto: solo sensores HC-SR04 y paneles LED (las cámaras van por HTTP)
type MQTTConfig struct {
	Enabled   bool
	BrokerURL string // tcp://host:1883
	Username  string
	Password  string
	ClientID  string
	// Distancia del HC-SR04 (cm): entre SensorMinCm y SensorOccupiedMaxCm hay un vehículo
	SensorMinCm         float64
	SensorOccupiedMaxCm float64
	// Segundos que una lectura debe mantenerse antes de cambiar el estado (filtra personas que pasan)
	SensorDebounceSeconds int
	// Cada cuántos segundos se revisa la disponibilidad para publicarla a los paneles si cambió
	AvailabilityPublishSeconds int
}

type RabbitMQConfig struct {
	URL      string
	Exchange string
	Queue    string
}

type LoggingConfig struct {
	Level   string
	LogFile string
}

var AppConfig *Config

// LoadConfig loads configuration from environment variables
func LoadConfig() *Config {
	// Load .env file
	if err := godotenv.Load(); err != nil {
		log.Println("No .env file found, using environment variables")
	}

	config := &Config{
		Server: ServerConfig{
			Port:    getEnv("SERVER_PORT", "8083"),
			Host:    getEnv("SERVER_HOST", "0.0.0.0"),
			GinMode: getEnv("GIN_MODE", "debug"),
		},
		MySQL: MySQLConfig{
			Host:         getEnv("MYSQL_HOST", "localhost"),
			Port:         getEnv("MYSQL_PORT", "3306"),
			User:         getEnv("MYSQL_USER", "root"),
			Password:     getEnv("MYSQL_PASSWORD", "root"),
			Database:     getEnv("MYSQL_DATABASE", "smart_parking_iot"),
			MaxOpenConns: getEnvAsInt("MYSQL_MAX_OPEN_CONNS", 25),
			MaxIdleConns: getEnvAsInt("MYSQL_MAX_IDLE_CONNS", 5),
		},
		Redis: RedisConfig{
			Host:     getEnv("REDIS_HOST", "localhost"),
			Port:     getEnv("REDIS_PORT", "6379"),
			Password: getEnv("REDIS_PASSWORD", ""),
			DB:       getEnvAsInt("REDIS_DB", 0),
			PoolSize: getEnvAsInt("REDIS_POOL_SIZE", 10),
		},
		Storage: StorageConfig{
			Provider:           getEnv("STORAGE_PROVIDER", "minio"),
			SignedURLHours:     getEnvAsInt("STORAGE_SIGNED_URL_HOURS", 168),
			GCSBucketName:      getEnv("GCS_BUCKET_NAME", ""),
			GCSCredentialsFile: getEnv("GOOGLE_APPLICATION_CREDENTIALS", ""),
		},
		S3: S3Config{
			Endpoint:   getEnv("S3_ENDPOINT", "localhost:9000"),
			AccessKey:  getEnv("S3_ACCESS_KEY", "minioadmin"),
			SecretKey:  getEnv("S3_SECRET_KEY", "minioadmin"),
			BucketName: getEnv("S3_BUCKET_NAME", "smart-parking"),
			UseSSL:     getEnvAsBool("S3_USE_SSL", false),
		},
		Eureka: EurekaConfig{
			ServerURL:         getEnv("EUREKA_SERVER_URL", "http://localhost:8761/eureka"),
			ServiceName:       getEnv("SERVICE_NAME", "iot-service"),
			ServiceInstanceID: getEnv("SERVICE_INSTANCE_ID", "iot-service-1"),
			ServiceIPAddress:  getEnv("SERVICE_IP_ADDRESS", "localhost"),
		},
		Services: ServicesConfig{
			IaServiceURL:      getEnv("IA_SERVICE_URL", "http://localhost:8084"),
			ParkingServiceURL: getEnv("PARKING_SERVICE_URL", "http://localhost:8082"),
			AuthServiceURL:    getEnv("AUTH_SERVICE_URL", "http://localhost:8081"),
			ServiceClientID:   getEnv("IOT_SERVICE_CLIENT_ID", "iot-service"),
			// Mismo valor que IOT_SERVICE_CLIENT_SECRET de backend/.env (lo valida auth-service)
			ServiceClientSecret:   getEnv("IOT_SERVICE_CLIENT_SECRET", ""),
			CaptureTimeoutSeconds: getEnvAsInt("CAPTURE_TIMEOUT_SECONDS", 15),
			DisplayCacheSeconds:   getEnvAsInt("DISPLAY_CACHE_SECONDS", 5),
		},
		MQTT: MQTTConfig{
			Enabled:                    getEnvAsBool("MQTT_ENABLED", true),
			BrokerURL:                  getEnv("MQTT_BROKER_URL", "tcp://localhost:1883"),
			Username:                   getEnv("MQTT_IOT_USERNAME", "iot-service"),
			Password:                   getEnv("MQTT_IOT_PASSWORD", ""),
			ClientID:                   getEnv("MQTT_CLIENT_ID", "iot-service-1"),
			SensorMinCm:                getEnvAsFloat("SENSOR_MIN_CM", 3),
			SensorOccupiedMaxCm:        getEnvAsFloat("SENSOR_OCCUPIED_MAX_CM", 80),
			SensorDebounceSeconds:      getEnvAsInt("SENSOR_DEBOUNCE_SECONDS", 5),
			AvailabilityPublishSeconds: getEnvAsInt("AVAILABILITY_PUBLISH_SECONDS", 5),
		},
		RabbitMQ: RabbitMQConfig{
			URL:      getEnv("RABBITMQ_URL", "amqp://guest:guest@localhost:5672/"),
			Exchange: getEnv("RABBITMQ_EXCHANGE", "smart-parking-events"),
			Queue:    getEnv("RABBITMQ_QUEUE", "iot-events"),
		},
		Logging: LoggingConfig{
			Level:   getEnv("LOG_LEVEL", "info"),
			LogFile: getEnv("LOG_FILE", "logs/iot-service.log"),
		},
	}

	AppConfig = config
	return config
}

// Helper functions
func getEnv(key, defaultValue string) string {
	value := os.Getenv(key)
	if value == "" {
		return defaultValue
	}
	return value
}

func getEnvAsInt(key string, defaultValue int) int {
	valueStr := os.Getenv(key)
	if valueStr == "" {
		return defaultValue
	}
	value, err := strconv.Atoi(valueStr)
	if err != nil {
		return defaultValue
	}
	return value
}

func getEnvAsBool(key string, defaultValue bool) bool {
	valueStr := os.Getenv(key)
	if valueStr == "" {
		return defaultValue
	}
	value, err := strconv.ParseBool(valueStr)
	if err != nil {
		return defaultValue
	}
	return value
}

func getEnvAsFloat(key string, defaultValue float64) float64 {
	value, err := strconv.ParseFloat(os.Getenv(key), 64)
	if err != nil {
		return defaultValue
	}
	return value
}
