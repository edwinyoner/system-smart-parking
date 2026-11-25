package com.winnersystems.smartparking.parking.application.dto.query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO de consulta para Rate.
 *
 * @param fractionAmount precio de una fracción (ej: S/ 0.50 cada 15 min)
 * @param usedByParkings parqueos que la usan en sus turnos (ParkingShiftRate)
 */
public record RateDto(
      Long id,
      String name,
      String description,
      BigDecimal amount,
      String currency,
      int billingUnitMinutes,
      int minimumMinutes,
      int graceMinutes,
      int toleranceMinutes,
      BigDecimal fractionAmount,
      Boolean status,
      List<String> usedByParkings,
      LocalDateTime createdAt,
      Long createdBy,
      LocalDateTime updatedAt,
      Long updatedBy,
      LocalDateTime deletedAt,
      Long deletedBy
) {
}
