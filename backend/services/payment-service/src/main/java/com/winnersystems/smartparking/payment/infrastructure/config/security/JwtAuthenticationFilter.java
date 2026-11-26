package com.winnersystems.smartparking.payment.infrastructure.config.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Filtro de autenticación JWT: arma el usuario con los claims userId, activeRole y permissions.
 *
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

   private final JwtService jwtService;

   @Override
   protected void doFilterInternal(
         @NonNull HttpServletRequest request,
         @NonNull HttpServletResponse response,
         @NonNull FilterChain filterChain
   ) throws ServletException, IOException {

      // 1. Extraer token del header Authorization
      String token = extractTokenFromRequest(request);

      // 2. Si hay token y es válido, autenticar
      if (token != null && jwtService.validateToken(token)) {
         try {
            // 3. Extraer claims del JWT (los emite auth-service)
            Claims claims = jwtService.extractAllClaims(token);
            Long userId = claims.get("userId", Long.class);
            String email = claims.getSubject();
            String activeRole = claims.get("activeRole", String.class);

            // 4. Autoridades: los permisos del rol activo y el rol ("ROLE_ADMIN")
            List<GrantedAuthority> authorities = new ArrayList<>();
            if (claims.get("permissions") instanceof Collection<?> permissions) {
               for (Object permission : permissions) {
                  authorities.add(new SimpleGrantedAuthority(String.valueOf(permission)));
               }
            }
            if (activeRole != null) {
               authorities.add(new SimpleGrantedAuthority("ROLE_" + activeRole));
            }
            log.debug("JWT válido: userId={}, activeRole={}, permisos={}", userId, activeRole, authorities.size());

            // 5. Usuario autenticado con su ID, para la auditoría
            CustomUserDetails userDetails = new CustomUserDetails(userId, email, activeRole, authorities);

            // 6. Establecer autenticación en el contexto de Spring Security
            UsernamePasswordAuthenticationToken authentication =
                  new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        authorities
                  );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("Usuario autenticado: userId={}", userId);

         } catch (Exception e) {
            // Token con claims inesperados: la petición sigue sin autenticar y Spring responde 401
            log.debug("JWT rechazado: {}", e.getClass().getSimpleName());
         }
      }

      // 7. Continuar con la cadena de filtros
      filterChain.doFilter(request, response);
   }

   /**
    * Extrae el token JWT del header Authorization.
    * Formato esperado: "Authorization: Bearer <token>"
    */
   private String extractTokenFromRequest(HttpServletRequest request) {
      String authHeader = request.getHeader("Authorization");

      if (authHeader != null && authHeader.startsWith("Bearer ")) {
         return authHeader.substring(7); // Remover "Bearer "
      }

      return null;
   }
}