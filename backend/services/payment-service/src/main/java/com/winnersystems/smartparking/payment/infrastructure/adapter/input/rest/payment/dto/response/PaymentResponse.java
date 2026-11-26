package com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.payment.dto.response;

import com.winnersystems.smartparking.payment.domain.model.ConfirmationMode;
import com.winnersystems.smartparking.payment.domain.model.PaymentChannel;
import com.winnersystems.smartparking.payment.domain.model.PaymentConcept;
import com.winnersystems.smartparking.payment.domain.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Pago. qrPayload y expiresAt solo en los automáticos */
public record PaymentResponse(
      Long id,
      PaymentConcept concept,
      Long sourceId,
      String description,
      String plateNumber,
      Long paymentTypeId,
      String paymentTypeCode,
      String paymentTypeName,
      ConfirmationMode confirmationMode,
      PaymentChannel channel,
      BigDecimal amount,
      String currency,
      PaymentStatus status,
      String gatewayReference,
      String qrPayload,
      LocalDateTime expiresAt,
      String operationNumber,
      Long operatorId,
      LocalDateTime paidAt,
      String failureReason,
      String cancelReason,
      BigDecimal refundedAmount,
      BigDecimal refundableAmount,
      String refundReason,
      LocalDateTime refundedAt,
      Long refundedBy,
      boolean settlementNotified,
      LocalDateTime createdAt,
      Long createdBy,
      LocalDateTime updatedAt,
      Long updatedBy
) {
}
