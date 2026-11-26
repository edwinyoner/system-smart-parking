package com.winnersystems.smartparking.parking.domain.exception;

import java.math.BigDecimal;

/**
 * El vehículo no puede salir: falta pagar la estadía (o la diferencia si se pasó del tiempo
 * de salida tras pagar). Se responde 409 con el monto pendiente en el mensaje.
 */
public class PaymentRequiredException extends ResourceConflictException {

   private final Long transactionId;
   private final BigDecimal amountDue;

   public PaymentRequiredException(Long transactionId, BigDecimal amountDue) {
      super("Falta pagar S/ " + amountDue.toPlainString() + " antes de salir (transacción " + transactionId + ")");
      this.transactionId = transactionId;
      this.amountDue = amountDue;
   }

   public Long getTransactionId() { return transactionId; }

   public BigDecimal getAmountDue() { return amountDue; }
}
