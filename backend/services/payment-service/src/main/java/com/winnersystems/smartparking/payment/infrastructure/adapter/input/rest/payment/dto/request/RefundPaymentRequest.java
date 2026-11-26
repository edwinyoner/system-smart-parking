package com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.payment.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundPaymentRequest {

   @NotNull(message = "Indica el monto a devolver")
   @DecimalMin(value = "0.01", message = "El monto a devolver debe ser mayor que cero")
   @Digits(integer = 8, fraction = 2, message = "El monto tiene como máximo 2 decimales")
   private BigDecimal amount;

   @NotBlank(message = "Indica el motivo de la devolución")
   @Size(max = 255, message = "El motivo no puede exceder 255 caracteres")
   private String reason;
}
