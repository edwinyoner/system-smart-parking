package com.winnersystems.smartparking.auth.infrastructure.adapter.input.rest.user.dto.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.List;

/**
 * Respuesta de GET /users/me: los datos del usuario más los permisos vigentes del rol activo.
 *
 * Los permisos del token se fijan al iniciar sesión; estos se leen de la base de datos en cada
 * petición. Así el panel oculta o muestra botones en cuanto un administrador cambia los permisos
 * del rol, sin volver a iniciar sesión.
 *
 * @param user              mismos campos que UserResponse (se serializan al mismo nivel)
 * @param activeRole        rol con el que inició sesión
 * @param activePermissions permisos actuales de ese rol, ordenados
 */
public record CurrentUserResponse(
      @JsonUnwrapped UserResponse user,
      String activeRole,
      List<String> activePermissions
) {
}
