package com.winnersystems.smartparking.parking.application.port.output;

import java.util.Optional;

/**
 * Consulta datos de usuarios en auth-service (responsables y operadores).
 */
public interface UserDirectoryPort {

   /**
    * @return el usuario, o vacío si no existe (o está eliminado)
    *
    * Si auth-service no responde o el usuario actual no tiene permiso para consultar
    * usuarios (users.read), el adaptador lanza la excepción correspondiente (503 o 403).
    */
   Optional<UserSummary> findUser(Long userId);

   /** Datos mínimos de un usuario */
   record UserSummary(Long id, String fullName, String email, boolean active) {
   }
}
