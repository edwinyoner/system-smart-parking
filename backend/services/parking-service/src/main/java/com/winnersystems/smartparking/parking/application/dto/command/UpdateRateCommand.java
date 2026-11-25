package com.winnersystems.smartparking.parking.application.dto.command;

import java.math.BigDecimal;

/**
 * Datos para actualizar una tarifa.
 *
 * @param status null = no cambia el estado
 */
public record UpdateRateCommand(
      String name,
      String description,
      BigDecimal amount,
      Integer billingUnitMinutes,
      Integer minimumMinutes,
      Integer graceMinutes,
      Integer toleranceMinutes,
      Boolean status
) {
}
