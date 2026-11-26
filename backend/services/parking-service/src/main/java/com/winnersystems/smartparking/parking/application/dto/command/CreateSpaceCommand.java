package com.winnersystems.smartparking.parking.application.dto.command;

/**
 * Datos para crear un espacio dentro de una zona.
 *
 * @param sensorId sensor HC-SR04 asignado (opcional; "tiene sensor" se deduce de él)
 */
public record CreateSpaceCommand(
      Long zoneId,
      String code,
      String type,
      String description,
      Double width,
      Double length,
      String sensorId,
      boolean hasCameraCoverage
) {
}
