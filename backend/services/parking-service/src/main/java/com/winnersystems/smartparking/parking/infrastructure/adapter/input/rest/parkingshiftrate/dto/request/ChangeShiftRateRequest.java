package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parkingshiftrate.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Cambia la tarifa de un turno del parqueo.
 */
public record ChangeShiftRateRequest(
      @NotNull(message = "La tarifa es obligatoria")
      @Positive(message = "Tarifa inválida")
      Long rateId
) {
}
