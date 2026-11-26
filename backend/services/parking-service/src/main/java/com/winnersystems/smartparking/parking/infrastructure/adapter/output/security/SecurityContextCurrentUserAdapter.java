package com.winnersystems.smartparking.parking.infrastructure.adapter.output.security;

import com.winnersystems.smartparking.parking.application.port.output.CurrentUserPort;
import com.winnersystems.smartparking.parking.infrastructure.config.security.CustomUserDetails;
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

   @Override
   public boolean hasAuthority(String authority) {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      return authentication != null && authority != null && authentication.getAuthorities().stream()
            .anyMatch(granted -> authority.equals(granted.getAuthority()));
   }
}
