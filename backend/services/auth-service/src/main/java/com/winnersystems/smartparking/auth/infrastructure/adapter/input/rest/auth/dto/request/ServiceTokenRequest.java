package com.winnersystems.smartparking.auth.infrastructure.adapter.input.rest.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Credenciales de un microservicio */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceTokenRequest {

   @NotBlank(message = "El clientId es obligatorio")
   private String clientId;

   @NotBlank(message = "El clientSecret es obligatorio")
   private String clientSecret;
}
