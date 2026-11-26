package com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.paymenttype.dto.request;

import com.winnersystems.smartparking.payment.domain.model.ConfirmationMode;
import com.winnersystems.smartparking.payment.domain.model.PaymentChannel;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/** Datos de un tipo de pago nuevo. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentTypeRequest {

   @NotBlank(message = "El código es obligatorio")
   @Pattern(regexp = "^[A-Za-z0-9_]{2,20}$", message = "El código tiene de 2 a 20 caracteres: letras, dígitos o guion bajo")
   private String code;

   @NotBlank(message = "El nombre es obligatorio")
   @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
   private String name;

   @Size(max = 255, message = "La descripción no puede exceder 255 caracteres")
   private String description;

   /** MANUAL (lo confirma un operador) o AUTOMATIC (lo confirma la pasarela) */
   @NotNull(message = "El modo de confirmación es obligatorio")
   private ConfirmationMode confirmationMode;

   /** Solo en los manuales: el operador debe anotar el número de operación */
   private Boolean requiresReference;

   /** EXIT_SCREEN, STREET_KIOSK, MOBILE_APP u OPERATOR */
   @NotEmpty(message = "Elige al menos un canal")
   private Set<PaymentChannel> channels;

   private Boolean status;
}
