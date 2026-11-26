package com.winnersystems.smartparking.payment.infrastructure.adapter.output.client.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * POST /internal/charges/{concept}/{sourceId}/settlement de parking-service: estado actual del pago.
 * parking-service lo registra una vez por paymentId y estado.
 */
public record SettlementRequest(
      Long paymentId,
      String status,
      BigDecimal amount,
      BigDecimal refundedAmount,
      LocalDateTime paidAt,
      String paymentTypeCode,
      String paymentTypeName,
      String operationNumber
) {
}
