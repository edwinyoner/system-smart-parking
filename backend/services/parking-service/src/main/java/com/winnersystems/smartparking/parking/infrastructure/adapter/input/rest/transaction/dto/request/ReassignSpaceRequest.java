package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.transaction.dto.request;

import jakarta.validation.constraints.NotNull;

/** Espacio donde realmente estacionó el vehículo (libre y de la misma zona) */
public record ReassignSpaceRequest(
      @NotNull(message = "Indica el espacio")
      Long spaceId
) {
}
