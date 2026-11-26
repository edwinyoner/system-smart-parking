package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.transaction.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motivo para descartar una lectura (no era un vehículo, foto ilegible...) */
public record DiscardPlateReviewRequest(
      @NotBlank(message = "Indica el motivo")
      @Size(max = 255, message = "El motivo no puede superar 255 caracteres")
      String reason
) {
}
