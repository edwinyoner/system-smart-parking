package com.winnersystems.smartparking.parking.domain.exception;

/**
 * El recurso pedido no existe o fue eliminado. Se responde 404.
 */
public class ResourceNotFoundException extends ParkingDomainException {

   public ResourceNotFoundException(String resource, Long id) {
      super(resource + " no encontrado con ID: " + id);
   }

   public ResourceNotFoundException(String message) {
      super(message);
   }
}
