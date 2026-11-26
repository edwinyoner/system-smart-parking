-- ============================================
-- Migración 001: zona de la cámara y decisión de parking-service
-- Para bases creadas con una versión anterior de scripts/init.sql.
-- Ejecutar una sola vez: mysql -h 127.0.0.1 -u root -p smart_parking_iot < scripts/migrations/001_capture_outcome.sql
-- ============================================

USE smart_parking_iot;

-- La cámara de entrada queda registrada con su zona (parking-service la exige para entrar)
ALTER TABLE devices
    ADD COLUMN zone_id BIGINT UNSIGNED NULL COMMENT 'Zona que vigila la cámara de entrada' AFTER parking_id,
    ADD INDEX idx_zone_id (zone_id);

-- Resultado de cada captura: qué decidió parking-service y si se abrió la barrera
ALTER TABLE camera_captures
    ADD COLUMN zone_id BIGINT UNSIGNED NULL COMMENT 'Zona de la cámara al momento de la captura' AFTER parking_id,
    ADD COLUMN review_id BIGINT UNSIGNED NULL COMMENT 'ID de la lectura en Por revisar' AFTER transaction_id,
    ADD COLUMN outcome VARCHAR(30) NULL COMMENT 'Decisión de parking-service' AFTER review_id,
    ADD COLUMN open_gate BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Si se ordenó abrir la barrera' AFTER outcome,
    ADD COLUMN result_message VARCHAR(255) NULL COMMENT 'Mensaje para la cámara' AFTER open_gate;

-- Cámara de prueba: zona 1 del parqueo 1 (ajustar al ID real de la zona en parking-service)
UPDATE devices SET zone_id = 1 WHERE device_id = 'ESP32-CAM-FRONTAL-001' AND zone_id IS NULL;
