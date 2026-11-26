package com.winnersystems.smartparking.payment.infrastructure.config.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Seguridad de payment-service: todo exige un JWT válido de auth-service, salvo el health check.
 * Cada endpoint pide además su permiso con @PreAuthorize (payment-types.*).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

   private final JwtAuthenticationFilter jwtAuthenticationFilter;

   @Bean
   public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
      http
            // API REST sin estado: CSRF no aplica
            .csrf(AbstractHttpConfigurer::disable)

            .authorizeHttpRequests(auth -> auth
                  .requestMatchers("/actuator/health", "/actuator/health/**", "/error").permitAll()
                  // Avisos de la pasarela: sin JWT; los protege la firma HMAC (GatewayWebhookRestAdapter)
                  .requestMatchers(HttpMethod.POST, "/payments/webhook").permitAll()
                  .anyRequest().authenticated()
            )

            // Sin token o con un token inválido: 401 (no 403), para que el cliente renueve la sesión
            .exceptionHandling(handling -> handling
                  .authenticationEntryPoint((request, response, ex) -> {
                     response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                     response.setContentType("application/json;charset=UTF-8");
                     response.getWriter().write(
                           "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Inicia sesión para continuar\"}");
                  })
            )

            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

      return http.build();
   }
}
