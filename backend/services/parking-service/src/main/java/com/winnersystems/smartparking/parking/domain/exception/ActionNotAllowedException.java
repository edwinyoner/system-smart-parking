package com.winnersystems.smartparking.parking.domain.exception;

/**
 * El usuario está autenticado pero no puede hacer esta acción concreta (por ejemplo, confirmar
 * la entrada de una revisión sin el permiso de registrar entradas). Se responde 403.
 */
public class ActionNotAllowedException extends ParkingDomainException {

   public ActionNotAllowedException(String message) {
      super(message);
   }
}
