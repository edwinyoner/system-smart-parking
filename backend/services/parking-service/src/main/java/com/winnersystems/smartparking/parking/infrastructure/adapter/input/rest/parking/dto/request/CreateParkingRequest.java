package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parking.dto.request;

import jakarta.validation.constraints.*;

/**
 * Datos para crear un parqueo. El dominio repite las validaciones (código en
 * mayúsculas, latitud y longitud juntas) para que no dependan del cliente.
 */
public record CreateParkingRequest(
      @NotBlank(message = "El nombre del parqueo es obligatorio")
      @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
      String name,

      @NotBlank(message = "El código del parqueo es obligatorio")
      @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{1,19}$",
            message = "El código debe tener de 2 a 20 caracteres: letras, dígitos, guion o guion bajo")
      String code,

      @Size(max = 500, message = "La descripción no puede exceder 500 caracteres")
      String description,

      @NotBlank(message = "La dirección es obligatoria")
      @Size(max = 255, message = "La dirección no puede exceder 255 caracteres")
      String address,

      @DecimalMin(value = "-90.0", message = "La latitud debe estar entre -90 y 90")
      @DecimalMax(value = "90.0", message = "La latitud debe estar entre -90 y 90")
      Double latitude,

      @DecimalMin(value = "-180.0", message = "La longitud debe estar entre -180 y 180")
      @DecimalMax(value = "180.0", message = "La longitud debe estar entre -180 y 180")
      Double longitude,

      /** Responsable opcional (ID de usuario de auth-service) */
      @Positive(message = "Responsable inválido")
      Long managerId
) {
}
