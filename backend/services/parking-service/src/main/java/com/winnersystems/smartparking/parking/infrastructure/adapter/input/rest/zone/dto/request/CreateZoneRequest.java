package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.zone.dto.request;

import jakarta.validation.constraints.*;

import java.util.List;

/**
 * Datos para crear una zona. La capacidad no se envía: se cuenta desde los espacios.
 */
public record CreateZoneRequest(
      @NotNull(message = "El parqueo es obligatorio")
      @Positive(message = "Parqueo inválido")
      Long parkingId,

      @NotBlank(message = "El nombre de la zona es obligatorio")
      @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
      String name,

      @NotBlank(message = "El código de la zona es obligatorio")
      @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{0,19}$",
            message = "El código debe tener de 1 a 20 caracteres: letras, dígitos, guion o guion bajo")
      String code,

      /** Opcional: si falta, vale la dirección del parqueo */
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
      List<String> cameraIds
) {
}
