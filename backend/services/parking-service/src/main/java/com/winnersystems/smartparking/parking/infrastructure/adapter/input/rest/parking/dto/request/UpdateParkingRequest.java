package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parking.dto.request;

import jakarta.validation.constraints.*;

/**
 * Actualización completa de un parqueo (el código no cambia): una ubicación o un
 * responsable que llega vacío se quita.
 */
public record UpdateParkingRequest(
      @NotBlank(message = "El nombre del parqueo es obligatorio")
      @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
      String name,

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

      @Positive(message = "Responsable inválido")
      Long managerId,

      /** Opcional: ACTIVE, INACTIVE, MAINTENANCE u OUT_OF_SERVICE (vacío = no cambia) */
      @Pattern(regexp = "^(ACTIVE|INACTIVE|MAINTENANCE|OUT_OF_SERVICE)?$", message = "Estado inválido")
      String status,

      // null = no cambia
      Boolean requireDriverDocument
) {
}
