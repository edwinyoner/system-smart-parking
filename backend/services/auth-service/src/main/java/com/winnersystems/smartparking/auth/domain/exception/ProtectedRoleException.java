package com.winnersystems.smartparking.auth.domain.exception;

/**
 * Se intentó una operación que la protección de roles no permite.
 *
 * @author Edwin Yoner Winner Systems - Smart Parking Platform
 * @version 1.0
 */
public class ProtectedRoleException extends RuntimeException {

   public ProtectedRoleException(String message) {
      super(message);
   }
}
