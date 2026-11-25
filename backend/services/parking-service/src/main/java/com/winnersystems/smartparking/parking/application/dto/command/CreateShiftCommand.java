package com.winnersystems.smartparking.parking.application.dto.command;

import java.time.LocalTime;

/**
 * Datos para crear un turno.
 *
 * @param status si nace activo; null = activo (lo habitual al crearlo desde el panel)
 */
public record CreateShiftCommand(
      String name,
      String code,
      String description,
      LocalTime startTime,
      LocalTime endTime,
      Boolean status
) {
}
