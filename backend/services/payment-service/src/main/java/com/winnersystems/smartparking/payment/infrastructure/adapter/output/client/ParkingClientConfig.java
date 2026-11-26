package com.winnersystems.smartparking.payment.infrastructure.adapter.output.client;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

/**
 * Configuración solo del cliente de parking-service (sin @Configuration a propósito: si fuera global,
 * también se aplicaría al cliente de auth-service, que es el que entrega el token).
 */
public class ParkingClientConfig {

   @Bean
   public RequestInterceptor serviceTokenInterceptor(ServiceTokenProvider tokenProvider) {
      return template -> template.header("Authorization", "Bearer " + tokenProvider.getToken());
   }
}
