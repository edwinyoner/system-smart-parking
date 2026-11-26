package services

import (
	"context"
	"fmt"
	"strings"
	"time"
)

// ImageStorage dónde se guardan las fotos de las cámaras. El handler solo conoce esta
// interfaz: cambiar de MinIO (desarrollo) a Google Cloud Storage (GCP) es configuración.
type ImageStorage interface {
	// UploadImage guarda la imagen y devuelve una URL firmada (temporal) para verla
	UploadImage(ctx context.Context, objectName string, data []byte, contentType string) (string, error)
	// EnsureBucket comprueba (o crea, si el proveedor lo permite) el bucket
	EnsureBucket(ctx context.Context) error
	// Provider nombre para los logs y el health check
	Provider() string
}

// Proveedores admitidos en STORAGE_PROVIDER
const (
	StorageMinIO = "minio"
	StorageGCS   = "gcs"
)

// StorageOptions configuración de ambos proveedores
type StorageOptions struct {
	Provider string
	// Vigencia de la URL firmada de cada foto (GCS V4 y S3 admiten como máximo 7 días)
	SignedURLExpiry time.Duration
	MinIO           S3Config
	GCS             GCSConfig
}

// MaxSignedURLExpiry máximo que admiten las URL firmadas V4
const MaxSignedURLExpiry = 7 * 24 * time.Hour

// NewImageStorage crea el almacenamiento elegido
func NewImageStorage(ctx context.Context, opts StorageOptions) (ImageStorage, error) {
	expiry := opts.SignedURLExpiry
	if expiry <= 0 || expiry > MaxSignedURLExpiry {
		expiry = MaxSignedURLExpiry
	}
	switch strings.ToLower(strings.TrimSpace(opts.Provider)) {
	case "", StorageMinIO:
		cfg := opts.MinIO
		cfg.URLExpiry = expiry
		return NewS3Service(cfg)
	case StorageGCS:
		cfg := opts.GCS
		cfg.URLExpiry = expiry
		return NewGCSStorage(ctx, cfg)
	default:
		return nil, fmt.Errorf("STORAGE_PROVIDER desconocido: %q (usa minio o gcs)", opts.Provider)
	}
}
