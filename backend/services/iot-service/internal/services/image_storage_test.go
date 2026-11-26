package services

import (
	"context"
	"testing"
)

func TestNewImageStorage_eligeProveedor(t *testing.T) {
	ctx := context.Background()

	s, err := NewImageStorage(ctx, StorageOptions{Provider: "", MinIO: S3Config{Endpoint: "localhost:9000", BucketName: "b"}})
	if err != nil || s.Provider() != StorageMinIO {
		t.Fatalf("sin proveedor se usa MinIO: %v %v", s, err)
	}
	if got := s.(*S3Service).urlExpiry; got != MaxSignedURLExpiry {
		t.Fatalf("vigencia por defecto 7 días, fue %v", got)
	}

	if _, err := NewImageStorage(ctx, StorageOptions{Provider: "gcs"}); err == nil {
		t.Fatal("GCS sin bucket debe fallar")
	}
	if _, err := NewImageStorage(ctx, StorageOptions{Provider: "s4"}); err == nil {
		t.Fatal("proveedor desconocido debe fallar")
	}
}
