package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parkingshiftrate.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Horarios y tarifas de un parqueo: reemplaza toda su configuración.
 * Una lista vacía deja al parqueo sin horario (no recibe vehículos).
 */
public record ConfigureParkingShiftRatesRequest(
      @NotNull(message = "Envía la lista de turnos (puede estar vacía)")
      @Size(max = 24, message = "Un parqueo admite como máximo 24 turnos")
      List<@Valid ShiftRateConfigRequest> configurations
) {

   /** @param status opcional, por defecto activo */
   public record ShiftRateConfigRequest(
         @NotNull(message = "El turno es obligatorio")
         @Positive(message = "Turno inválido")
         Long shiftId,

         @NotNull(message = "La tarifa es obligatoria")
         @Positive(message = "Tarifa inválida")
         Long rateId,

         Boolean status
   ) {
   }
}
