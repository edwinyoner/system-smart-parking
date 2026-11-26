package com.winnersystems.smartparking.parking.application.dto.query;

import com.winnersystems.smartparking.parking.domain.model.ChargeConcept;

import java.math.BigDecimal;

/**
 * Cuánto se debe por una estadía o una multa (lo consulta payment-service antes de cobrar).
 *
 * @param payable false si no se puede pagar ahora; reason explica por qué
 */
public record ChargeDto(
      ChargeConcept concept,
      Long sourceId,
      BigDecimal amountDue,
      String currency,
      String description,
      String plateNumber,
      boolean payable,
      String reason
) {
}
