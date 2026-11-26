package com.winnersystems.smartparking.payment.domain.exception;

/** Recurso inexistente o eliminado: 404 */
public class ResourceNotFoundException extends PaymentDomainException {

   public ResourceNotFoundException(String resource, Long id) {
      super(resource + " no encontrado con ID: " + id);
   }

   public ResourceNotFoundException(String message) {
      super(message);
   }
}
