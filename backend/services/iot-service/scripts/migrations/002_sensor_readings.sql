-- Cambios de estado de los sensores HC-SR04 (MQTT). Para una base creada antes de este cambio.
-- Uso: mysql -u root -p iot_service_db < scripts/migrations/002_sensor_readings.sql

CREATE TABLE IF NOT EXISTS sensor_readings (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(50) NOT NULL COMMENT 'Sensor HC-SR04 (igual a Space.sensorId)',
    parking_id BIGINT UNSIGNED NULL,
    distance_cm DECIMAL(6,1) NOT NULL COMMENT 'Distancia medida al confirmar el cambio',
    occupied BOOLEAN NOT NULL,
    outcome VARCHAR(30) NULL COMMENT 'Respuesta de parking-service: SPACE_OCCUPIED, SPACE_RELEASED, STAY_MOVED, NO_CHANGE',
    space_code VARCHAR(20) NULL,
    error VARCHAR(255) NULL COMMENT 'Motivo si parking-service no la aceptó',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_sensor_device (device_id),
    INDEX idx_sensor_parking (parking_id),
    INDEX idx_sensor_created (created_at)
) ENGINE=InnoDB COMMENT='Cambios de estado estables de los sensores HC-SR04 (no cada lectura)';
