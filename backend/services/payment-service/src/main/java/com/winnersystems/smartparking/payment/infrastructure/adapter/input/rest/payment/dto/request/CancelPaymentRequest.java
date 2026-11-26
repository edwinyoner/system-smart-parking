package com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CancelPaymentRequest {

   @NotBlank(message = "Indica el motivo de la cancelación")
   @Size(max = 255, message = "El motivo no puede exceder 255 caracteres")
   private String reason;
}
