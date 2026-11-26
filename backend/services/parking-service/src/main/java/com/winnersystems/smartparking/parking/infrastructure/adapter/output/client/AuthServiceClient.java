package com.winnersystems.smartparking.parking.infrastructure.adapter.output.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * Cliente OpenFeign para comunicación con auth-service.
 *
 * Permite a parking-service obtener información de usuarios/operadores
 * desde auth-service de manera síncrona.
 *
 * Va directo por Eureka (lb://auth-service), sin pasar por el gateway: es tráfico interno.
 * auth-service no tiene el prefijo /auth en sus rutas (lo quita el gateway), por eso
 * aquí no hay "path". FeignAuthConfig reenvía el token del usuario en cada llamada.
 *
 * @author Edwin Yoner - Winner Systems - Smart Parking Platform
 * @version 1.0
 */
@FeignClient(name = "auth-service")
public interface AuthServiceClient {

   /**
    * Obtiene la lista de operadores activos desde auth-service.
    *
    * Llama a: GET http://auth-service/users/operators (exige users.read)
    *
    * @return lista de operadores activos
    */
   @GetMapping("/users/operators")
   List<OperatorDto> getActiveOperators();

   /**
    * Obtiene un usuario específico por ID desde auth-service.
    *
    * Llama a: GET http://auth-service/users/{userId} (exige users.read)
    *
    * @param userId ID del usuario
    * @return información del usuario
    */
   @GetMapping("/users/{userId}")
   UserDto getUserById(@PathVariable("userId") Long userId);

   // ========================= DTOs =========================

   /**
    * DTO para recibir información de operador desde auth-service.
    * Debe coincidir con OperatorDto de auth-service.
    */
   record OperatorDto(
         Long id,
         String firstName,
         String lastName,
         String email,
         String phoneNumber,
         Boolean status
   ) {
      public String getFullName() {
         return firstName + " " + lastName;
      }
   }

   /**
    * DTO para recibir información de usuario desde auth-service.
    */
   record UserDto(
         Long id,
         String firstName,
         String lastName,
         String fullName,
         String email,
         String phoneNumber,
         String profilePicture,
         Boolean status,
         Boolean emailVerified
   ) {
      public String getFullName() {
         if (fullName != null && !fullName.isBlank()) {
            return fullName;
         }
         return (firstName + " " + lastName).trim();
      }
   }
}