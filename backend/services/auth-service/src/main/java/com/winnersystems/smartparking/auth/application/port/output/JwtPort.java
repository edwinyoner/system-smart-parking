package com.winnersystems.smartparking.auth.application.port.output;

import java.time.Instant;
import java.util.Set;

/**
 * Puerto de salida para operaciones JWT.
 *
 * @author Edwin Yoner Winner Systems - Smart Parking Platform
 * @version 1.0
 */
public interface JwtPort {

   /**
    * Genera un Access Token JWT con información completa del usuario.
    *
    * @param userId          ID del usuario
    * @param email           Email del usuario
    * @param firstName       Nombre del usuario
    * @param lastName        Apellido del usuario
    * @param phoneNumber     Teléfono del usuario
    * @param profilePicture  URL de foto de perfil
    * @param status          Estado activo/inactivo
    * @param emailVerified   Email verificado o no
    * @param activeRole      ROL CON EL QUE INICIÓ SESIÓN
    * @param roles           Todos los roles del usuario
    * @param permissions     Permisos del usuario
    * @return Access Token JWT
    */
   String generateAccessToken(
         Long userId,
         String email,
         String firstName,
         String lastName,
         String phoneNumber,
         String profilePicture,
         Boolean status,
         Boolean emailVerified,
         String activeRole,
         Set<String> roles,
         Set<String> permissions
   );

   /**
    * Token de servicio (client credentials): tokenType = SERVICE, sujeto "service:<clientId>",
    * sin userId ni roles y con permisos internos.
    */
   String generateServiceToken(String clientId, Set<String> permissions);

   /** Duración de un token de servicio, en segundos */
   long serviceTokenExpirationSeconds();

   /** true si el token es de un microservicio (tokenType = SERVICE), no de un usuario */
   boolean isServiceToken(String token);

   String extractEmail(String token);
   boolean validateToken(String token);
   Long extractUserId(String token);
   String extractActiveRole(String token);
   Instant extractIssuedAt(String token);
}