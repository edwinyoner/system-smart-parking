package repository

import (
	"time"

	"github.com/edwinyoner/winner-systems-system-smart-parking/backend/services/iot-service/internal/models"
	"gorm.io/gorm"
)

// SensorReadingRepository cambios de estado de los sensores HC-SR04
type SensorReadingRepository struct {
	db *gorm.DB
}

func NewSensorReadingRepository(db *gorm.DB) *SensorReadingRepository {
	return &SensorReadingRepository{db: db}
}

// Create guarda un cambio de estado
func (r *SensorReadingRepository) Create(reading *models.SensorReading) error {
	return r.db.Create(reading).Error
}

// FindByDeviceID últimos cambios de un sensor
func (r *SensorReadingRepository) FindByDeviceID(deviceID string, limit int) ([]models.SensorReading, error) {
	var readings []models.SensorReading
	err := r.db.Where("device_id = ?", deviceID).Order("created_at DESC").Limit(limit).Find(&readings).Error
	return readings, err
}

// DeleteOlderThan limpieza de historial antiguo
func (r *SensorReadingRepository) DeleteOlderThan(before time.Time) (int64, error) {
	result := r.db.Where("created_at < ?", before).Delete(&models.SensorReading{})
	return result.RowsAffected, result.Error
}
