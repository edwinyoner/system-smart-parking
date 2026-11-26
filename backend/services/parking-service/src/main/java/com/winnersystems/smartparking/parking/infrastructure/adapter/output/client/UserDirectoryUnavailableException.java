package com.winnersystems.smartparking.parking.infrastructure.adapter.output.client;

/**
 * auth-service no respondió al consultar un usuario. Se responde 503.
 */
public class UserDirectoryUnavailableException extends RuntimeException {

   public UserDirectoryUnavailableException(String message, Throwable cause) {
      super(message, cause);
   }
}
