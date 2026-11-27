package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.infraction.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record EditInfractionRequest(
      @NotBlank(message = "El tipo es obligatorio") String infractionType,
      @NotBlank(message = "La gravedad es obligatoria") String severity,
      @NotBlank(message = "La descripción es obligatoria") @Size(min = 5, max = 1000) String description,
      @Size(max = 5, message = "Se admiten hasta 5 enlaces de evidencia") List<@Size(max = 500) String> evidence,
      @Size(max = 500) String notes) {
}
