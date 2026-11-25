package com.winnersystems.smartparking.auth.domain.exception;

/**
 * El rol no existe o fue eliminado.
 *
 * @author Edwin Yoner Winner Systems - Smart Parking Platform
 * @version 1.0
 */
public class RoleNotFoundException extends RuntimeException {

   public RoleNotFoundException(Long id) {
      super("Rol no encontrado: " + id);
   }
}
