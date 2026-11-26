package models

import (
	"encoding/json"
	"time"
)

// DeviceType representa el tipo de dispositivo IoT
type DeviceType string

const (
	DeviceTypeESP32CamFrontal DeviceType = "ESP32_CAM_FRONTAL"
	DeviceTypeESP32CamAerial  DeviceType = "ESP32_CAM_AERIAL"
	DeviceTypeESP32Display    DeviceType = "ESP32_DISPLAY"
	DeviceTypeSensorHCSR04    DeviceType = "SENSOR_HC_SR04"
)

// DeviceStatus representa el estado de un dispositivo
type DeviceStatus string

const (
	DeviceStatusActive      DeviceStatus = "ACTIVE"
	DeviceStatusInactive    DeviceStatus = "INACTIVE"
	DeviceStatusMaintenance DeviceStatus = "MAINTENANCE"
)

// Device representa un dispositivo IoT registrado en el sistema
type Device struct {
	ID         uint64     `gorm:"primaryKey;autoIncrement" json:"id"`
	DeviceID   string     `gorm:"type:varchar(50);uniqueIndex;not null" json:"deviceId"`
	DeviceType DeviceType `gorm:"type:enum('ESP32_CAM_FRONTAL','ESP32_CAM_AERIAL','ESP32_DISPLAY','SENSOR_HC_SR04');not null" json:"deviceType"`
	ParkingID  *uint64    `gorm:"index" json:"parkingId,omitempty"`
	// Zona de parking-service que vigila la cámara de entrada (parking-service la exige para entrar)
	ZoneID        *uint64      `gorm:"index" json:"zoneId,omitempty"`
	Location      string       `gorm:"type:varchar(200)" json:"location,omitempty"`
	IPAddress     string       `gorm:"type:varchar(45)" json:"ipAddress,omitempty"`
	Status        DeviceStatus `gorm:"type:enum('ACTIVE','INACTIVE','MAINTENANCE');default:'ACTIVE'" json:"status"`
	LastHeartbeat *time.Time   `json:"lastHeartbeat,omitempty"`
	Metadata      string       `gorm:"type:json" json:"metadata,omitempty"`
	CreatedAt     time.Time    `gorm:"autoCreateTime" json:"createdAt"`
	UpdatedAt     time.Time    `gorm:"autoUpdateTime" json:"updatedAt"`
}

// TableName especifica el nombre de la tabla en MySQL
func (Device) TableName() string {
	return "devices"
}

// IsActive verifica si el dispositivo está activo
// IsAvenuePanel panel de avenida: no está en un parqueo, orienta hacia varios
func (d *Device) IsAvenuePanel() bool {
	return d.DeviceType == DeviceTypeESP32Display && d.ParkingID == nil
}

// Rotación del panel de avenida: segundos que se muestra cada parqueo
const (
	DefaultRotationSeconds = 10
	minRotationSeconds     = 5
	maxRotationSeconds     = 60
)

// panelMetadata configuración opcional de un panel de avenida, en devices.metadata:
// {"parking_ids":[1,2], "rotation_seconds":10}
type panelMetadata struct {
	ParkingIDs      []uint64 `json:"parking_ids"`
	RotationSeconds int      `json:"rotation_seconds"`
}

func (d *Device) panelMetadata() panelMetadata {
	var meta panelMetadata
	if d.Metadata != "" {
		_ = json.Unmarshal([]byte(d.Metadata), &meta)
	}
	return meta
}

// PanelParkingIDs parqueos que rota un panel de avenida, en orden. Vacío = todos los publicados.
func (d *Device) PanelParkingIDs() []uint64 {
	return d.panelMetadata().ParkingIDs
}

// PanelRotationSeconds segundos por parqueo (10 por defecto, entre 5 y 60)
func (d *Device) PanelRotationSeconds() int {
	seconds := d.panelMetadata().RotationSeconds
	if seconds < minRotationSeconds || seconds > maxRotationSeconds {
		return DefaultRotationSeconds
	}
	return seconds
}

func (d *Device) IsActive() bool {
	return d.Status == DeviceStatusActive
}

// UpdateHeartbeat actualiza el timestamp del último heartbeat
func (d *Device) UpdateHeartbeat() {
	now := time.Now()
	d.LastHeartbeat = &now
}
