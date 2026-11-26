package com.winnersystems.smartparking.payment.infrastructure.adapter.output.security;

import com.winnersystems.smartparking.payment.application.port.output.CurrentUserPort;
import com.winnersystems.smartparking.payment.infrastructure.config.security.CustomUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Adaptador de CurrentUserPort: lee el usuario que JwtAuthenticationFilter dejó en el contexto.
 */
@Component
public class SecurityContextCurrentUserAdapter implements CurrentUserPort {

   @Override
   public Optional<Long> getCurrentUserId() {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails user) {
         return Optional.ofNullable(user.getId());
      }
      return Optional.empty();
   }
}
