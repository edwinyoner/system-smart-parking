package com.winnersystems.smartparking.auth.infrastructure.config.security;

import com.winnersystems.smartparking.auth.application.port.output.UserPersistencePort;
import com.winnersystems.smartparking.auth.domain.model.Permission;
import com.winnersystems.smartparking.auth.domain.model.Role;
import com.winnersystems.smartparking.auth.domain.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;  // AGREGAR

import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Implementación de UserDetailsService de Spring Security.
 *
 * <p>Spring Security usa esta clase para cargar los datos del usuario
 * cuando se autentica (en el JwtAuthenticationFilter).</p>
 *
 * <p>Convierte nuestro User (domain) en CustomUserDetails (que incluye userId).</p>
 *
 * @author Edwin Yoner Winner Systems - Smart Parking Platform
 * @version 1.0
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

   private final UserPersistencePort userPersistencePort;

   public UserDetailsServiceImpl(UserPersistencePort userPersistencePort) {
      this.userPersistencePort = userPersistencePort;
   }

   @Override
   @Transactional(readOnly = true)
   public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
      // 1. Buscar usuario por email
      User user = userPersistencePort.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException(
                  "Usuario no encontrado con email: " + email));

      // 2. Convertir a CustomUserDetails (incluye userId)
      return new CustomUserDetails(
            user.getId(),                    // userId
            user.getEmail(),                 // username
            user.getPassword(),              // password
            true,                            // accountNonExpired
            user.isFullyActive(),            // accountNonLocked
            true,                            // credentialsNonExpired
            user.isFullyActive(),            // enabled
            getAuthoritiesByRole(user),      // autoridades de cada rol activo
            user.getPasswordChangedAt() == null ? null
                  : user.getPasswordChangedAt().atZone(ZoneId.systemDefault()).toInstant()
      );
   }

   /**
    * Autoridades de cada rol activo del usuario: el rol ("ROLE_ADMIN") y sus
    * permisos ("users.create"). El filtro JWT elige las del rol de la sesión.
    */
   private Map<String, Set<GrantedAuthority>> getAuthoritiesByRole(User user) {
      Map<String, Set<GrantedAuthority>> authoritiesByRole = new HashMap<>();

      for (Role role : user.getRoles()) {
         if (role.isInactive()) {
            continue;
         }
         Set<GrantedAuthority> authorities = new HashSet<>();
         authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));
         for (Permission permission : role.getPermissions()) {
            authorities.add(new SimpleGrantedAuthority(permission.getName()));
         }
         authoritiesByRole.put(role.getName(), authorities);
      }

      return authoritiesByRole;
   }
}
