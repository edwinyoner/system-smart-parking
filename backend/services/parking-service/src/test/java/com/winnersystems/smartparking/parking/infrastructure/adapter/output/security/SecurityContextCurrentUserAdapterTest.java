package com.winnersystems.smartparking.parking.infrastructure.adapter.output.security;

import com.winnersystems.smartparking.parking.infrastructure.config.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CurrentUserPort devuelve el usuario del JWT para la auditoría.
 */
class SecurityContextCurrentUserAdapterTest {

   private final SecurityContextCurrentUserAdapter adapter = new SecurityContextCurrentUserAdapter();

   @AfterEach
   void clear() {
      SecurityContextHolder.clearContext();
   }

   @Test
   @DisplayName("con usuario autenticado devuelve su ID")
   void authenticated() {
      CustomUserDetails user = new CustomUserDetails(7L, "ana@x.pe", "ADMIN", List.of());
      SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
      assertThat(adapter.getCurrentUserId()).contains(7L);
      assertThat(adapter.requireCurrentUserId()).isEqualTo(7L);
   }

   @Test
   @DisplayName("sin usuario: vacío, y requireCurrentUserId falla")
   void anonymous() {
      assertThat(adapter.getCurrentUserId()).isEmpty();
      assertThatThrownBy(adapter::requireCurrentUserId).isInstanceOf(IllegalStateException.class);
   }
}
