package com.winnersystems.smartparking.parking.application.dto.command;

/**
 * Datos editables de un espacio (la zona y el código no cambian). Es una
 * actualización completa: medidas o sensor que llegan vacíos se quitan.
 *
 * @param status AVAILABLE, MAINTENANCE u OUT_OF_SERVICE (null = no cambia)
 */
public record UpdateSpaceCommand(
      String type,
      String description,
      Double width,
      Double length,
      String sensorId,
      boolean hasCameraCoverage,
      String status
) {
}
