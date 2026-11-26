package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Aviso de payment-service con el estado actual de un pago.
 * paymentTypeCode, paymentTypeName y operationNumber llegan para registro, pero no se guardan:
 * el detalle del pago vive en payment-service.
 */
public record SettlementRequest(
      @NotNull(message = "El ID del pago es obligatorio") Long paymentId,
      @NotBlank(message = "El estado del pago es obligatorio") String status,
      @NotNull(message = "El monto es obligatorio") @PositiveOrZero BigDecimal amount,
      @PositiveOrZero BigDecimal refundedAmount,
      LocalDateTime paidAt,
      String paymentTypeCode,
      String paymentTypeName,
      String operationNumber
) {
}
