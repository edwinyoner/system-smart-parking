package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.transaction.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cancelar una estadía registrada por error */
public record CancelTransactionRequest(
      @NotBlank(message = "Indica el motivo de la cancelación")
      @Size(max = 500, message = "El motivo no puede superar 500 caracteres")
      String reason
) {
}
