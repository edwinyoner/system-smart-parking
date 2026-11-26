package com.winnersystems.smartparking.parking.application.dto.command;

/**
 * Datos para crear un parqueo.
 *
 * @param managerId responsable opcional; su nombre se consulta en auth-service
 */
public record CreateParkingCommand(
      String name,
      String code,
      String description,
      String address,
      Double latitude,
      Double longitude,
      Long managerId
) {
}
