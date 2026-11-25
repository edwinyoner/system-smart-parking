package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.rate.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Respuesta de una tarifa con sus reglas de cobro y los 6 campos de auditoría.
 *
 * @param usedByParkings parqueos que la usan: Angular no ofrece eliminarla y avisa al desactivarla
 */
public record RateResponse(
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
