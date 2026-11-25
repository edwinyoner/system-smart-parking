package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.shift.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Respuesta de un turno. Las horas van como "HH:mm" (sin segundos).
 *
 * @param usedByParkings parqueos que lo usan en sus tarifas: Angular avisa antes de desactivarlo
 *                       y no ofrece eliminarlo
 */
public record ShiftResponse(
      Long id,
      String name,
      String code,
      String description,
      @JsonFormat(pattern = "HH:mm") LocalTime startTime,
      @JsonFormat(pattern = "HH:mm") LocalTime endTime,
      Boolean status,
      boolean crossesMidnight,
      double durationHours,
      List<String> usedByParkings,
      LocalDateTime createdAt,
      Long createdBy,
      LocalDateTime updatedAt,
      Long updatedBy,
      LocalDateTime deletedAt,
      Long deletedBy
) {
}
