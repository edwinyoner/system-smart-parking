package com.winnersystems.smartparking.payment.domain.exception;

/** Otro microservicio (parking-service, auth-service) no respondió: 503 */
public class ExternalServiceException extends RuntimeException {
   public ExternalServiceException(String message, Throwable cause) {
      super(message, cause);
   }
}
