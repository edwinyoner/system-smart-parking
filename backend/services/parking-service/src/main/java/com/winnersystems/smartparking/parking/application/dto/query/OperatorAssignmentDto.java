package com.winnersystems.smartparking.parking.application.dto.query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Asignación de un operador con los datos de su parqueo, zona y turno.
 *
 * @param scope    PARKING (todo el parqueo) o ZONE
 * @param status   PENDING, ACTIVE o ENDED
 * @param onDutyNow vigente hoy y dentro del horario de su turno en este momento
 */
public record OperatorAssignmentDto(
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
      LocalTime shiftStartTime,
      LocalTime shiftEndTime,
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
