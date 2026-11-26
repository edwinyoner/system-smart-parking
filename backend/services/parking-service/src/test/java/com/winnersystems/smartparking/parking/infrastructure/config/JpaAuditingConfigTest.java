package com.winnersystems.smartparking.parking.infrastructure.config;

import com.winnersystems.smartparking.parking.infrastructure.config.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El autor de cada registro sale del token. Un token de servicio (iot-service) no trae ID
 * de usuario: antes Optional.of(null) lanzaba NullPointerException y la entrada automática
 * por cámara respondía 500.
 */
class JpaAuditingConfigTest {

   private final AuditorAware<Long> auditor = new JpaAuditingConfig().auditorProvider();

   @AfterEach
   void limpiar() {
      SecurityContextHolder.clearContext();
   }

   private void autenticar(CustomUserDetails principal) {
      SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
   }

   @Test
   void usuarioConIdEsElAutor() {
      autenticar(new CustomUserDetails(7L, "operador@correo.pe", "OPERADOR",
            List.of(new SimpleGrantedAuthority("transactions.entry"))));

      assertThat(auditor.getCurrentAuditor()).contains(7L);
   }

   @Test
   void tokenDeServicioQuedaSinAutor() {
      autenticar(new CustomUserDetails(null, "service:iot-service", null,
            List.of(new SimpleGrantedAuthority("SERVICE_CLIENT"))));

      assertThat(auditor.getCurrentAuditor()).isEqualTo(Optional.empty());
   }

   @Test
   void sinSesionQuedaSinAutor() {
      assertThat(auditor.getCurrentAuditor()).isEmpty();
   }
}
