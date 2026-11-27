package com.winnersystems.smartparking.parking.application.dto.query;

import com.winnersystems.smartparking.parking.domain.model.ChargeConcept;

import java.math.BigDecimal;

/**
 * Cuánto se debe por una estadía o una multa (lo consulta payment-service antes de cobrar).
 *
 * @param parkingId   parqueo de la estadía o de la multa (payment-service lo copia para sus reportes)
 * @param payable false si no se puede pagar ahora; reason explica por qué
 */
public record ChargeDto(
      ChargeConcept concept,
      Long sourceId,
      BigDecimal amountDue,
      String currency,
      String description,
      String plateNumber,
      Long parkingId,
      String parkingName,
      boolean payable,
      String reason
) {
}
