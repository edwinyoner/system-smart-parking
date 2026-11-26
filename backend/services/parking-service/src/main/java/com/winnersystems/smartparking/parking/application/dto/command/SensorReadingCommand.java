package com.winnersystems.smartparking.parking.application.dto.command;

/**
 * Lectura ya estable de un sensor HC-SR04 (iot-service filtra el ruido antes de enviarla).
 *
 * @param sensorId ID del sensor asignado al espacio (Space.sensorId)
 * @param occupied true si ve un vehículo
 */
public record SensorReadingCommand(String sensorId, boolean occupied) {
}
