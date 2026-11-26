package com.winnersystems.smartparking.payment.domain.exception;

/** Código repetido o registro en uso: 409 */
public class ResourceConflictException extends PaymentDomainException {

   public ResourceConflictException(String message) {
      super(message);
   }
}
