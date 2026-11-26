package com.winnersystems.smartparking.payment.application.dto.query;

import com.winnersystems.smartparking.payment.domain.model.ConfirmationMode;
import com.winnersystems.smartparking.payment.domain.model.PaymentChannel;

import java.time.LocalDateTime;
import java.util.Set;

/** Tipo de pago para mostrar */
public record PaymentTypeDto(
      Long id,
      String code,
      String name,
      String description,
      ConfirmationMode confirmationMode,
      boolean requiresReference,
      Set<PaymentChannel> channels,
      Boolean status,
      long paymentsCount,                 // pagos hechos con este tipo (con pagos no se elimina)
      LocalDateTime createdAt,
      Long createdBy,
      LocalDateTime updatedAt,
      Long updatedBy,
      LocalDateTime deletedAt,
      Long deletedBy
) {
}
