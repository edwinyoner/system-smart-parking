package com.winnersystems.smartparking.parking.application.dto.query;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * DTO de consulta para Shift.
 *
 * @param crossesMidnight true si termina al día siguiente (ej: 20:00 - 05:59)
 * @param durationHours   duración contando el minuto final (06:00 - 13:59 = 8 h)
 * @param usedByParkings  nombres de los parqueos que lo usan en sus tarifas (ParkingShiftRate)
 */
public record ShiftDto(
      Long id,
      String name,
      String code,
      String description,
      LocalTime startTime,
      LocalTime endTime,
      Boolean status,
      boolean crossesMidnight,
      double durationHours,
      List<String> usedByParkings,
      LocalDateTime createdAt,
      Long createdBy,
      LocalDateTime updatedAt,
      Long updatedBy
) {
}
