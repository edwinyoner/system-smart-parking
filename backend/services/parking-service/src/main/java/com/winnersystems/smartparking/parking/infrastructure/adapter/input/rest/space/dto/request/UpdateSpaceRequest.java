package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.space.dto.request;

import jakarta.validation.constraints.*;

/**
 * Actualización completa de un espacio (la zona y el código no cambian).
 */
public record UpdateSpaceRequest(
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

      Boolean hasCameraCoverage,

      /** Opcional: AVAILABLE, MAINTENANCE u OUT_OF_SERVICE (OCCUPIED solo por entrada o detección) */
      @Pattern(regexp = "^(AVAILABLE|OCCUPIED|MAINTENANCE|OUT_OF_SERVICE)?$", message = "Estado inválido")
      String status
) {
}
