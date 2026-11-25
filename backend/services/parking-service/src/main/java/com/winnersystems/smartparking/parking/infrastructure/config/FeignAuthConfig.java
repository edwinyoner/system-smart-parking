package com.winnersystems.smartparking.parking.infrastructure.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Reenvía el token del usuario en las llamadas Feign a otros microservicios.
 *
 * auth-service protege sus endpoints con permisos (p. ej. users.read): la llamada interna
 * se hace en nombre del usuario que originó la petición y con sus mismos permisos.
 * Así ningún servicio confía ciegamente en otro (defensa en profundidad).
 */
@Configuration
public class FeignAuthConfig {

   @Bean
   public RequestInterceptor forwardAuthorizationHeader() {
      return template -> {
         if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            String authorization = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null && !template.headers().containsKey(HttpHeaders.AUTHORIZATION)) {
               template.header(HttpHeaders.AUTHORIZATION, authorization);
            }
         }
      };
   }
}
