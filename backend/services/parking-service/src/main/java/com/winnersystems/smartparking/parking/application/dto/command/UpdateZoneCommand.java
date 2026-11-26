package com.winnersystems.smartparking.parking.application.dto.command;

import java.util.List;

/**
 * Datos editables de una zona (el parqueo y el código no cambian). Es una
 * actualización completa: ubicación o cámaras que llegan vacías se quitan.
 *
 * @param status nuevo estado opcional (null = no cambia)
 */
public record UpdateZoneCommand(
      String name,
      String address,
      String description,
      Double latitude,
      Double longitude,
      List<String> cameraIds,
      String status
) {
}
