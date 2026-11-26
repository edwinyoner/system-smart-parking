package com.winnersystems.smartparking.payment.infrastructure.config.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Usuario autenticado, armado con los claims del JWT que emite auth-service.
 *
 * payment-service no tiene tabla de usuarios: confía en el token firmado por auth-service.
 * Las autoridades son los permisos del rol activo ("payment-types.create") y el propio rol
 * ("ROLE_ADMIN"), igual que en auth-service, para proteger cada endpoint por permiso.
 */
public class CustomUserDetails implements UserDetails {

   private final Long id;
   private final String email;
   private final String activeRole;
   private final Collection<? extends GrantedAuthority> authorities;

   public CustomUserDetails(
         Long id,
         String email,
         String activeRole,
         Collection<? extends GrantedAuthority> authorities
   ) {
      this.id = id;
      this.email = email;
      this.activeRole = activeRole;
      this.authorities = authorities == null ? List.of() : List.copyOf(authorities);
   }

   /** ID del usuario en auth-service (para la auditoría) */
   public Long getId() {
      return id;
   }

   public String getEmail() {
      return email;
   }

   /** Rol con el que inició sesión (claim activeRole) */
   public String getActiveRole() {
      return activeRole;
   }

   @Override
   public Collection<? extends GrantedAuthority> getAuthorities() {
      return authorities;
   }

   @Override
   public String getPassword() {
      return null; // No aplica: el token ya fue validado
   }

   @Override
   public String getUsername() {
      return email;
   }

   @Override
   public boolean isAccountNonExpired() {
      return true;
   }

   @Override
   public boolean isAccountNonLocked() {
      return true;
   }

   @Override
   public boolean isCredentialsNonExpired() {
      return true;
   }

   @Override
   public boolean isEnabled() {
      return true;
   }
}
