package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.transaction.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Salida manual desde el panel: por estadía o por placa. El operador sale del token.
 * El documento solo se pide si el parqueo lo exige y se entró con documento.
 */
public record RecordExitRequest(
      Long transactionId,

      @Size(max = 10, message = "La placa no puede superar 10 caracteres")
      String plateNumber,

      Long exitDocumentTypeId,

      @Size(max = 20, message = "El documento no puede superar 20 caracteres")
      String exitDocumentNumber,

      @Size(max = 500, message = "Las observaciones no pueden superar 500 caracteres")
      String notes
) {
}
