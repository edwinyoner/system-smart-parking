package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.zone.dto.request;

import jakarta.validation.constraints.*;

import java.util.List;

/**
 * Actualización completa de una zona (el parqueo y el código no cambian).
 */
public record UpdateZoneRequest(
      @NotBlank(message = "El nombre de la zona es obligatorio")
      @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
      String name,

      @Size(max = 200, message = "La dirección no puede exceder 200 caracteres")
      String address,

      @Size(max = 500, message = "La descripción no puede exceder 500 caracteres")
      String description,

      @DecimalMin(value = "-90.0", message = "La latitud debe estar entre -90 y 90")
      @DecimalMax(value = "90.0", message = "La latitud debe estar entre -90 y 90")
      Double latitude,

      @DecimalMin(value = "-180.0", message = "La longitud debe estar entre -180 y 180")
      @DecimalMax(value = "180.0", message = "La longitud debe estar entre -180 y 180")
      Double longitude,

      @Size(max = 10, message = "Una zona admite como máximo 10 cámaras")
      List<String> cameraIds,

      /** Opcional: ACTIVE, INACTIVE, MAINTENANCE u OUT_OF_SERVICE (vacío = no cambia) */
      @Pattern(regexp = "^(ACTIVE|INACTIVE|MAINTENANCE|OUT_OF_SERVICE)?$", message = "Estado inválido")
      String status
) {
}
