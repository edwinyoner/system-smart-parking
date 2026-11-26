package com.winnersystems.smartparking.payment.application.dto.command;

import com.winnersystems.smartparking.payment.domain.model.ConfirmationMode;
import com.winnersystems.smartparking.payment.domain.model.PaymentChannel;

import java.util.Set;

/** Datos para crear un tipo de pago */
public record CreatePaymentTypeCommand(
      String code,
      String name,
      String description,
      ConfirmationMode confirmationMode,
      boolean requiresReference,
      Set<PaymentChannel> channels,
      Boolean status
) {
}
