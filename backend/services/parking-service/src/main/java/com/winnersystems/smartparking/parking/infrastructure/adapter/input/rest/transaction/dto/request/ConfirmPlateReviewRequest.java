package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.transaction.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Placa confirmada por el operador mirando la foto; documento opcional */
public record ConfirmPlateReviewRequest(
      @NotBlank(message = "Indica la placa")
      @Size(max = 10, message = "La placa no puede superar 10 caracteres")
      String plateNumber,

      Long documentTypeId,

      @Size(max = 20, message = "El documento no puede superar 20 caracteres")
      String documentNumber
) {
}
