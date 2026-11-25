package com.winnersystems.smartparking.auth.infrastructure.config.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Implementación personalizada de UserDetails.
 * Agrega el userId al principal para facilitar su acceso en los controllers.
 *
 * @author Edwin Yoner Winner Systems - Smart Parking Platform
 * @version 1.0
 */
@Getter
public class CustomUserDetails implements UserDetails {

   private final Long userId;
   private final String email;
   private final String password;
   private final boolean accountNonExpired;
   private final boolean accountNonLocked;
   private final boolean credentialsNonExpired;
   private final boolean enabled;
   private final Map<String, Set<GrantedAuthority>> authoritiesByRole;
   private final Instant passwordChangedAt; // null = nunca cambió
   private final String activeRole; // Rol elegido al iniciar sesión (viene del JWT)

   public CustomUserDetails(
         Long userId,
         String email,
         String password,
         boolean accountNonExpired,
         boolean accountNonLocked,
         boolean credentialsNonExpired,
         boolean enabled,
         Map<String, Set<GrantedAuthority>> authoritiesByRole,
         Instant passwordChangedAt) {
      this(userId, email, password, accountNonExpired, accountNonLocked,
            credentialsNonExpired, enabled, authoritiesByRole, passwordChangedAt, null);
   }

   private CustomUserDetails(
         Long userId,
         String email,
         String password,
         boolean accountNonExpired,
         boolean accountNonLocked,
         boolean credentialsNonExpired,
         boolean enabled,
         Map<String, Set<GrantedAuthority>> authoritiesByRole,
         Instant passwordChangedAt,
         String activeRole) {

      this.userId = userId;
      this.email = email;
      this.password = password;
      this.accountNonExpired = accountNonExpired;
      this.accountNonLocked = accountNonLocked;
      this.credentialsNonExpired = credentialsNonExpired;
      this.enabled = enabled;
      this.authoritiesByRole = authoritiesByRole;
      this.passwordChangedAt = passwordChangedAt;
      this.activeRole = activeRole;
   }

   /**
    * Indica si un token sigue valiendo: la cuenta está habilitada y la
    * contraseña no cambió después de emitirlo.
    */
   public boolean acceptsTokenIssuedAt(Instant issuedAt) {
      // El JWT guarda segundos: se compara con la misma precisión para no
      // rechazar un inicio de sesión hecho justo después del cambio
      return enabled && (passwordChangedAt == null
            || !issuedAt.isBefore(passwordChangedAt.truncatedTo(ChronoUnit.SECONDS)));
   }

   /**
    * Devuelve una copia con el rol activo. Solo se acepta si el usuario
    * todavía tiene ese rol: el token puede ser anterior a que se lo quitaran.
    */
   public CustomUserDetails withActiveRole(String role) {
      boolean stillAssigned = role != null && authoritiesByRole.containsKey(role);

      return new CustomUserDetails(userId, email, password, accountNonExpired, accountNonLocked,
            credentialsNonExpired, enabled, authoritiesByRole, passwordChangedAt, stillAssigned ? role : null);
   }

   /** Solo las del rol activo: los demás roles del usuario no suman permisos. */
   @Override
   public Collection<? extends GrantedAuthority> getAuthorities() {
      return activeRole == null ? Set.of() : authoritiesByRole.get(activeRole);
   }

   @Override
   public String getPassword() {
      return password;
   }

   @Override
   public String getUsername() {
      return email;
   }

   @Override
   public boolean isAccountNonExpired() {
      return accountNonExpired;
   }

   @Override
   public boolean isAccountNonLocked() {
      return accountNonLocked;
   }

   @Override
   public boolean isCredentialsNonExpired() {
      return credentialsNonExpired;
   }

   @Override
   public boolean isEnabled() {
      return enabled;
   }
}