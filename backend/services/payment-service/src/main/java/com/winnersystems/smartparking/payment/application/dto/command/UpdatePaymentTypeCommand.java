package com.winnersystems.smartparking.payment.application.dto.command;

import com.winnersystems.smartparking.payment.domain.model.ConfirmationMode;
import com.winnersystems.smartparking.payment.domain.model.PaymentChannel;

import java.util.Set;

/** Datos editables de un tipo de pago (el código no cambia) */
public record UpdatePaymentTypeCommand(
      String name,
      String description,
      ConfirmationMode confirmationMode,
      boolean requiresReference,
      Set<PaymentChannel> channels,
      Boolean status
) {
}
