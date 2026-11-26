package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.internal.dto;

import com.winnersystems.smartparking.parking.domain.model.ChargeConcept;

import java.math.BigDecimal;

/** Cuánto se debe por una estadía o una multa */
public record ChargeResponse(
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
