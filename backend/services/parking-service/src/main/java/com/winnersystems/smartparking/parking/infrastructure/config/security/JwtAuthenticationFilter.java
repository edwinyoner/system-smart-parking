package com.winnersystems.smartparking.parking.infrastructure.config.security;

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
 * Filtro de autenticación JWT.
 * Intercepta cada request, valida el JWT token y extrae el userId para auditoría.
 *
 * @author Edwin Yoner - Winner Systems - Smart Parking Platform
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

   /** Claim y valor que marcan un token de servicio (lo emite auth-service en /service-token) */
   static final String SERVICE_TOKEN_TYPE = "SERVICE";
   /** Prefijo de los permisos que solo puede traer un token de servicio */
   public static final String INTERNAL_PREFIX = "internal.";
   /** Autoridad de todo token de servicio (no empieza con ROLE_: ningún rol humano la produce) */
   public static final String SERVICE_CLIENT_AUTHORITY = "SERVICE_CLIENT";

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
            boolean serviceToken = SERVICE_TOKEN_TYPE.equals(claims.get("tokenType", String.class));

            // 4. Autoridades. Usuario: permisos del rol activo y el rol ("ROLE_ADMIN").
            //    Microservicio: solo permisos internos (internal.*) y SERVICE_CLIENT.
            //    Un token de usuario nunca recibe permisos internos, aunque los traiga.
            List<GrantedAuthority> authorities = new ArrayList<>();
            if (claims.get("permissions") instanceof Collection<?> permissions) {
               for (Object permission : permissions) {
                  String name = String.valueOf(permission);
                  boolean internal = name.startsWith(INTERNAL_PREFIX) || SERVICE_CLIENT_AUTHORITY.equals(name);
                  if (serviceToken == internal) {
                     authorities.add(new SimpleGrantedAuthority(name));
                  }
               }
            }

            CustomUserDetails userDetails;
            if (serviceToken) {
               authorities.add(new SimpleGrantedAuthority(SERVICE_CLIENT_AUTHORITY));
               // 5a. Microservicio: sin userId; la auditoría de sus acciones la lleva quien la origina
               userDetails = new CustomUserDetails(null, claims.getSubject(), null, authorities);
               log.debug("Token de servicio válido: {}, permisos={}", claims.getSubject(), authorities.size());
            } else {
               Long userId = claims.get("userId", Long.class);
               String activeRole = claims.get("activeRole", String.class);
               if (activeRole != null) {
                  authorities.add(new SimpleGrantedAuthority("ROLE_" + activeRole));
               }
               // 5b. Usuario autenticado con su ID, para la auditoría
               userDetails = new CustomUserDetails(userId, claims.getSubject(), activeRole, authorities);
               log.debug("JWT válido: userId={}, activeRole={}, permisos={}", userId, activeRole, authorities.size());
            }

            // 6. Establecer autenticación en el contexto de Spring Security
            UsernamePasswordAuthenticationToken authentication =
                  new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        authorities
                  );

            SecurityContextHolder.getContext().setAuthentication(authentication);

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