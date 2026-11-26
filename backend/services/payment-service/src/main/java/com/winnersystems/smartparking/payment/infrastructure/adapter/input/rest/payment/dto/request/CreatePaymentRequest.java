package com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.payment.dto.request;

import com.winnersystems.smartparking.payment.domain.model.PaymentChannel;
import com.winnersystems.smartparking.payment.domain.model.PaymentConcept;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Pagar una estadía o una multa. Sin monto: lo calcula parking-service */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentRequest {

   @NotNull(message = "Indica qué se paga: PARKING_STAY (estadía) o FINE (multa)")
   private PaymentConcept concept;

   @NotNull(message = "Indica el ID de la estadía o de la multa")
   @Positive(message = "El ID debe ser positivo")
   private Long sourceId;

   @NotNull(message = "El tipo de pago es obligatorio")
   private Long paymentTypeId;

   /** Solo en los automáticos (QR); un manual siempre es OPERATOR */
   private PaymentChannel channel;

   /** N.° de operación, si el tipo manual lo exige */
   @Size(max = 64, message = "El número de operación no puede exceder 64 caracteres")
   private String operationNumber;
}
