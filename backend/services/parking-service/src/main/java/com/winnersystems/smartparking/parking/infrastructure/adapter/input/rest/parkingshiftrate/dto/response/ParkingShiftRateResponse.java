package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parkingshiftrate.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Un turno del parqueo con su tarifa.
 *
 * @param usable activo y con turno y tarifa activos: en ese horario el parqueo atiende y cobra esa tarifa
 */
public record ParkingShiftRateResponse(
      Long id,
      Long parkingId,
      Long shiftId,
      String shiftName,
      String shiftCode,
      @JsonFormat(pattern = "HH:mm") LocalTime startTime,
      @JsonFormat(pattern = "HH:mm") LocalTime endTime,
      boolean crossesMidnight,
      boolean shiftUsable,
      Long rateId,
      String rateName,
      BigDecimal rateAmount,
      String rateCurrency,
      boolean rateUsable,
      boolean status,
      boolean usable,
      LocalDateTime createdAt,
      Long createdBy,
      LocalDateTime updatedAt,
      Long updatedBy
) {
}
