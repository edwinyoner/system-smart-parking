package com.winnersystems.smartparking.payment.domain.exception;

/** Regla de negocio de pagos incumplida: 400 */
public class PaymentDomainException extends RuntimeException {

   public PaymentDomainException(String message) {
      super(message);
   }
}
