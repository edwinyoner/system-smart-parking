package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.operatorassignment.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/**
 * Asignar un operador.
 *
 * @param zoneId    opcional: sin zona atiende todo el parqueo
 * @param startDate opcional: hoy
 * @param endDate   opcional: indefinida
 */
public record AssignOperatorRequest(
      @NotNull(message = "El operador es obligatorio") @Positive(message = "Operador inválido") Long operatorId,
      @NotNull(message = "El parqueo es obligatorio") @Positive(message = "Parqueo inválido") Long parkingId,
      @Positive(message = "Zona inválida") Long zoneId,
      @NotNull(message = "El turno es obligatorio") @Positive(message = "Turno inválido") Long shiftId,
      LocalDate startDate,
      LocalDate endDate
) {
}
