package com.winnersystems.smartparking.parking.application.dto.query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Un turno del parqueo con su tarifa.
 *
 * @param shiftUsable el turno está activo y no eliminado
 * @param rateUsable  la tarifa está activa y no eliminada
 * @param usable      la configuración está activa y su turno y tarifa se pueden usar:
 *                    en ese horario el parqueo recibe vehículos y cobra esa tarifa
 */
public record ParkingShiftRateDto(
      Long id,
      Long parkingId,
      Long shiftId,
      String shiftName,
      String shiftCode,
      LocalTime startTime,
      LocalTime endTime,
      boolean crossesMidnight,
      boolean shiftUsable,
      Long rateId,
      String rateName,
      BigDecimal rateAmount,
      String currency,
      boolean rateUsable,
      boolean status,
      boolean usable,
      LocalDateTime createdAt,
      Long createdBy,
      LocalDateTime updatedAt,
      Long updatedBy
) {
}
