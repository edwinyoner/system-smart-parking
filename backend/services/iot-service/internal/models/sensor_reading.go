package models

import "time"

// SensorReading cambio de estado estable de un sensor HC-SR04 (no se guarda cada lectura:
// el sensor mide cada 1-2 s y la tabla crecería sin aportar nada)
type SensorReading struct {
	ID         uint64    `gorm:"primaryKey;autoIncrement" json:"id"`
	DeviceID   string    `gorm:"type:varchar(50);not null;index" json:"deviceId"`
	ParkingID  *uint64   `gorm:"index" json:"parkingId,omitempty"`
	DistanceCm float64   `gorm:"type:decimal(6,1);not null" json:"distanceCm"`
	Occupied   bool      `gorm:"not null" json:"occupied"`
	Outcome    *string   `gorm:"type:varchar(30)" json:"outcome,omitempty"` // lo que hizo parking-service
	SpaceCode  *string   `gorm:"type:varchar(20)" json:"spaceCode,omitempty"`
	Error      *string   `gorm:"type:varchar(255)" json:"error,omitempty"`
	CreatedAt  time.Time `gorm:"autoCreateTime;index" json:"createdAt"`
}

// TableName especifica el nombre de la tabla en MySQL
func (SensorReading) TableName() string {
	return "sensor_readings"
}
