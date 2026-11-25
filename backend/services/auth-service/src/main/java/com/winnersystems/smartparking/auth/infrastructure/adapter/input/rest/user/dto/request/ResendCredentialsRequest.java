package com.winnersystems.smartparking.auth.infrastructure.adapter.input.rest.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO para enviar credenciales nuevas.
 * La contraseña va en el cuerpo: en la URL quedaría registrada en logs.
 *
 * @author Edwin Yoner Winner Systems - Smart Parking Platform
 * @version 1.0
 */
public record ResendCredentialsRequest(
      @NotBlank(message = "Password es requerido")
      @Size(min = 8, max = 128, message = "Password debe tener entre 8 y 128 caracteres")
      @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
            message = "Password debe contener: 1 mayúscula, 1 minúscula, 1 número y 1 carácter especial"
      )
      String password
) {
   /** Evita que la contraseña aparezca si el objeto se escribe en un log. */
   @Override
   public String toString() {
      return "ResendCredentialsRequest[password=***]";
   }
}
