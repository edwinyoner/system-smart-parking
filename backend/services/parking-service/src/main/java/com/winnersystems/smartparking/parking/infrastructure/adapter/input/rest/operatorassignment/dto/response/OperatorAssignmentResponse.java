package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.operatorassignment.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Asignación de un operador.
 *
 * @param scope     PARKING (todo el parqueo) o ZONE
 * @param status    PENDING, ACTIVE o ENDED
 * @param onDutyNow en este momento le toca atender
 */
public record OperatorAssignmentResponse(
      Long id,
      Long operatorId,
      String operatorName,
      Long parkingId,
      String parkingName,
      String parkingCode,
      String scope,
      Long zoneId,
      String zoneName,
      String zoneCode,
      Long shiftId,
      String shiftName,
      @JsonFormat(pattern = "HH:mm") LocalTime shiftStartTime,
      @JsonFormat(pattern = "HH:mm") LocalTime shiftEndTime,
      boolean shiftCrossesMidnight,
      LocalDate startDate,
      LocalDate endDate,
      String status,
      boolean onDutyNow,
      LocalDateTime createdAt,
      Long createdBy,
      LocalDateTime updatedAt,
      Long updatedBy
) {
}
