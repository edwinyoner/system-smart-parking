package com.winnersystems.smartparking.parking.infrastructure.config.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuración de Spring Security para parking-service.
 * Configura autenticación basada en JWT tokens.
 *
 * @author Edwin Yoner - Winner Systems - Smart Parking Platform
 * @version 1.0
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
            // Deshabilitar CSRF (no necesario para APIs REST stateless)
            .csrf(AbstractHttpConfigurer::disable)

            // Configurar autorización de requests
            .authorizeHttpRequests(auth -> auth
                  // ========== ENDPOINTS PÚBLICOS (TEMPORALMENTE) ==========
                  // SOLO PARA DESARROLLO - En producción quitar estos
                  .requestMatchers(
                        "/actuator/**",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/error",
                        // TEMPORAL: módulos aún sin revisar. Al revisar cada uno se quita de aquí
                        // y sus endpoints se protegen con @PreAuthorize por permiso.
                        "/parkings/**",
                        "/zones/**",
                        "/spaces/**",
                        "/payment-types/**",
                        "/transactions/**"
                  ).permitAll()

                  // Todos los demás endpoints requieren autenticación
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

            // Sin estado (stateless) - No crear sesiones
            .sessionManagement(session ->
                  session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Agregar filtro JWT antes del filtro de autenticación estándar
            .addFilterBefore(
                  jwtAuthenticationFilter,
                  UsernamePasswordAuthenticationFilter.class
            );

      return http.build();
   }
}