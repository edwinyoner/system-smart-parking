package com.winnersystems.smartparking.parking.application.dto.command;

import java.time.LocalTime;

/**
 * Datos para actualizar un turno. El código no se cambia: lo referencian las tarifas.
 *
 * @param status null = no cambia el estado
 */
public record UpdateShiftCommand(
      String name,
      String description,
      LocalTime startTime,
      LocalTime endTime,
      Boolean status
) {
}
