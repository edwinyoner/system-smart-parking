package com.winnersystems.smartparking.parking.application.dto.command;

import java.util.List;

/**
 * Datos para crear una zona dentro de un parqueo. La capacidad no se indica:
 * se cuenta desde los espacios que se creen en la zona.
 */
public record CreateZoneCommand(
      Long parkingId,
      String name,
      String code,
      String address,
      String description,
      Double latitude,
      Double longitude,
      List<String> cameraIds
) {
}
