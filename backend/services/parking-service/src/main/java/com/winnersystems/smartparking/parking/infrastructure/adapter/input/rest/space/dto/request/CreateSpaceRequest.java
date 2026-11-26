package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.space.dto.request;

import jakarta.validation.constraints.*;

/**
 * Datos para crear un espacio. "Tiene sensor" se deduce de sensorId.
 */
public record CreateSpaceRequest(
      @NotNull(message = "La zona es obligatoria")
      @Positive(message = "Zona inválida")
      Long zoneId,

      @NotBlank(message = "El código del espacio es obligatorio")
      @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{0,19}$",
            message = "El código debe tener de 1 a 20 caracteres: letras, dígitos, guion o guion bajo")
      String code,

      @NotBlank(message = "El tipo de espacio es obligatorio")
      @Pattern(regexp = "^(PARALLEL|DIAGONAL|PERPENDICULAR)$", message = "El tipo debe ser PARALLEL, DIAGONAL o PERPENDICULAR")
      String type,

      @Size(max = 255, message = "La descripción no puede exceder 255 caracteres")
      String description,

      @DecimalMin(value = "0.5", message = "El ancho debe estar entre 0.5 y 20 metros")
      @DecimalMax(value = "20.0", message = "El ancho debe estar entre 0.5 y 20 metros")
      Double width,

      @DecimalMin(value = "0.5", message = "El largo debe estar entre 0.5 y 20 metros")
      @DecimalMax(value = "20.0", message = "El largo debe estar entre 0.5 y 20 metros")
      Double length,

      @Size(max = 50, message = "El ID del sensor no puede exceder 50 caracteres")
      String sensorId,

      Boolean hasCameraCoverage
) {
}
