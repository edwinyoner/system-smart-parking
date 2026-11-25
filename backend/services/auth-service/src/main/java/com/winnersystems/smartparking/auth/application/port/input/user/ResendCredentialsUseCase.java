package com.winnersystems.smartparking.auth.application.port.input.user;

/**
 * Caso de uso: Reenviar credenciales por email.
 *
 * @author Edwin Yoner Winner Systems - Smart Parking Platform
 * @version 1.0
 */
public interface ResendCredentialsUseCase {

   /**
    * Guarda la contraseña temporal y la envía al correo del usuario.
    * Si el correo falla, la contraseña anterior se conserva.
    *
    * @param userId ID del usuario
    * @param plainPassword contraseña temporal en texto plano
    * @param actorId ID de quien realiza la acción
    * @param actorActiveRole rol con el que inició sesión
    */
   void execute(Long userId, String plainPassword, Long actorId, String actorActiveRole);
}