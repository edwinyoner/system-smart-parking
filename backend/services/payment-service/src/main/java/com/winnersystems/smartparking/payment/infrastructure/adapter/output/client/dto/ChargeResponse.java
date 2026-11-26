package com.winnersystems.smartparking.payment.infrastructure.adapter.output.client.dto;

import java.math.BigDecimal;

/** GET /internal/charges/{concept}/{sourceId} de parking-service */
public record ChargeResponse(
      String concept,
      Long sourceId,
      BigDecimal amountDue,
      String currency,
      String description,
      String plateNumber,
      boolean payable,
      String reason
) {
}
