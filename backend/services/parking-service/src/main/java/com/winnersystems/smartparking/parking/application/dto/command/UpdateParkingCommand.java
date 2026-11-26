package com.winnersystems.smartparking.parking.application.dto.command;

/**
 * Datos editables de un parqueo (el código no cambia).
 *
 * Es una actualización completa: una ubicación o un responsable en null se quitan.
 *
 * @param status nuevo estado opcional (null = no cambia)
 */
public record UpdateParkingCommand(
      String name,
      String description,
      String address,
      Double latitude,
      Double longitude,
      Long managerId,
      String status,
      Boolean requireDriverDocument   // null = no cambia
) {
}
