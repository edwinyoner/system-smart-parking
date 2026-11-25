package com.winnersystems.smartparking.auth.domain.exception;

/**
 * Excepción lanzada cuando una operación no está permitida sobre un usuario protegido.
 *
 * Cuándo se lanza:
 * - Un usuario intenta desactivarse, eliminarse o cambiar sus propios roles
 * - Alguien sin ADMIN como rol activo intenta administrar a un ADMIN
 *
 * @author Edwin Yoner - Winner Systems - Smart Parking Platform
 * @version 1.0
 */
public class ProtectedUserException extends RuntimeException {

   public ProtectedUserException(String message) {
      super(message);
   }
}
