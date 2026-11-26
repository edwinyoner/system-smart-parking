package services

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"io"
	"time"

	"cloud.google.com/go/storage"
	"google.golang.org/api/option"
)

// GCSConfig Google Cloud Storage
type GCSConfig struct {
	BucketName string
	// Archivo JSON de una cuenta de servicio. Vacío = credenciales por defecto de Google
	// (en una VM de Compute Engine, la cuenta de servicio de la VM; en local,
	// `gcloud auth application-default login` o GOOGLE_APPLICATION_CREDENTIALS).
	CredentialsFile string
	URLExpiry       time.Duration
}

// GCSStorage fotos en un bucket privado de GCS: nunca son públicas, se ven con URL firmada.
// La cuenta de servicio necesita roles/storage.objectCreator y roles/storage.objectViewer
// en el bucket y, sin archivo de clave, roles/iam.serviceAccountTokenCreator sobre sí misma
// (para firmar las URL con la API de IAM).
type GCSStorage struct {
	client *storage.Client
	bucket *storage.BucketHandle
	name   string
	expiry time.Duration
}

// NewGCSStorage crea el cliente de GCS
func NewGCSStorage(ctx context.Context, cfg GCSConfig) (*GCSStorage, error) {
	if cfg.BucketName == "" {
		return nil, errors.New("falta GCS_BUCKET_NAME")
	}
	var opts []option.ClientOption
	if cfg.CredentialsFile != "" {
		opts = append(opts, option.WithCredentialsFile(cfg.CredentialsFile))
	}
	client, err := storage.NewClient(ctx, opts...)
	if err != nil {
		return nil, fmt.Errorf("no se pudo crear el cliente de GCS: %w", err)
	}
	expiry := cfg.URLExpiry
	if expiry <= 0 || expiry > MaxSignedURLExpiry {
		expiry = MaxSignedURLExpiry
	}
	return &GCSStorage{client: client, bucket: client.Bucket(cfg.BucketName), name: cfg.BucketName, expiry: expiry}, nil
}

// Provider implementa ImageStorage
func (g *GCSStorage) Provider() string { return StorageGCS }

// EnsureBucket comprueba que el bucket existe y es accesible. No lo crea: el bucket
// (región, retención, acceso uniforme) se define al preparar la infraestructura en GCP.
func (g *GCSStorage) EnsureBucket(ctx context.Context) error {
	if _, err := g.bucket.Attrs(ctx); err != nil {
		if errors.Is(err, storage.ErrBucketNotExist) {
			return fmt.Errorf("el bucket %s no existe en GCS", g.name)
		}
		return fmt.Errorf("no se pudo acceder al bucket %s: %w", g.name, err)
	}
	return nil
}

// UploadImage implementa ImageStorage
func (g *GCSStorage) UploadImage(ctx context.Context, objectName string, data []byte, contentType string) (string, error) {
	w := g.bucket.Object(objectName).NewWriter(ctx)
	w.ContentType = contentType
	w.CacheControl = "private, max-age=0"
	if _, err := io.Copy(w, bytes.NewReader(data)); err != nil {
		_ = w.Close()
		return "", fmt.Errorf("error al subir imagen a GCS: %w", err)
	}
	if err := w.Close(); err != nil {
		return "", fmt.Errorf("error al subir imagen a GCS: %w", err)
	}
	url, err := g.bucket.SignedURL(objectName, &storage.SignedURLOptions{
		Scheme:  storage.SigningSchemeV4,
		Method:  "GET",
		Expires: time.Now().Add(g.expiry),
	})
	if err != nil {
		return "", fmt.Errorf("error al firmar la URL de la imagen: %w", err)
	}
	return url, nil
}

// Close libera el cliente al apagar el servicio
func (g *GCSStorage) Close() error {
	return g.client.Close()
}
