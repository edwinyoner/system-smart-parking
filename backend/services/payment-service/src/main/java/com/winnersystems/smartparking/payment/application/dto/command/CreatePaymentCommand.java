package com.winnersystems.smartparking.payment.application.dto.command;

import com.winnersystems.smartparking.payment.domain.model.PaymentChannel;
import com.winnersystems.smartparking.payment.domain.model.PaymentConcept;

/**
 * Pagar una estadía o una multa. No lleva monto: lo calcula parking-service.
 *
 * @param operationNumber solo en pagos manuales cuyo tipo lo exige
 */
public record CreatePaymentCommand(
      PaymentConcept concept,
      Long sourceId,
      Long paymentTypeId,
      PaymentChannel channel,
      String operationNumber
) {
}
