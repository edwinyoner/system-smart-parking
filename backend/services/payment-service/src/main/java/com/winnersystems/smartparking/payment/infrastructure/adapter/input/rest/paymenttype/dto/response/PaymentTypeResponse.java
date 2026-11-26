package com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.paymenttype.dto.response;

import com.winnersystems.smartparking.payment.domain.model.ConfirmationMode;
import com.winnersystems.smartparking.payment.domain.model.PaymentChannel;

import java.time.LocalDateTime;
import java.util.List;

/** Respuesta de un tipo de pago. Los canales van ordenados como en PaymentChannel */
public record PaymentTypeResponse(
      Long id,
      String code,
      String name,
      String description,
      ConfirmationMode confirmationMode,
      boolean requiresReference,
      List<PaymentChannel> channels,
      Boolean status,
      long paymentsCount,
      LocalDateTime createdAt,
      Long createdBy,
      LocalDateTime updatedAt,
      Long updatedBy,
      LocalDateTime deletedAt,
      Long deletedBy
) {
}
