package com.winnersystems.smartparking.parking.domain.exception;

/**
 * La operación choca con el estado actual de los datos: un código ya usado o un
 * registro en uso que no se puede eliminar. Se responde 409.
 */
public class ResourceConflictException extends ParkingDomainException {

   public ResourceConflictException(String message) {
      super(message);
   }
}
