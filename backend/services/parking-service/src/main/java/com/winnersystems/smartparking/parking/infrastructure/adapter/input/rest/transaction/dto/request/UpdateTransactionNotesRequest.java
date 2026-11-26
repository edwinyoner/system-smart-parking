package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.transaction.dto.request;

import jakarta.validation.constraints.Size;

/** Observaciones de una estadía (vacío las borra) */
public record UpdateTransactionNotesRequest(
      @Size(max = 500, message = "Las observaciones no pueden superar 500 caracteres")
      String notes
) {
}
