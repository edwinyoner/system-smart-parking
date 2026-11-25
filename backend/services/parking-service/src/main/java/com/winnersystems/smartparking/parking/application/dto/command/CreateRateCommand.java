package com.winnersystems.smartparking.parking.application.dto.command;

import java.math.BigDecimal;

/**
 * Datos para crear una tarifa. Las reglas de cobro en null toman su valor por defecto
 * (por hora o fracción, sin mínimo, 5 min sin costo y 5 min de tolerancia).
 *
 * @param status null = activa
 */
public record CreateRateCommand(
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
