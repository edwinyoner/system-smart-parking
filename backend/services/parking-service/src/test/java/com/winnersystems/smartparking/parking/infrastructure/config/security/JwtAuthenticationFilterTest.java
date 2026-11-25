package com.winnersystems.smartparking.parking.infrastructure.config.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El filtro arma el usuario con los claims que emite auth-service:
 * userId, activeRole y permissions (no "role", que el token no trae).
 */
class JwtAuthenticationFilterTest {

   /** Secreto de prueba (solo para el test, no es el real) */
   private static final String SECRET = "clave-de-prueba-solo-para-tests-0123456789-abcdefghij";

   private JwtAuthenticationFilter filter;

   @BeforeEach
   void setUp() {
      JwtService jwtService = new JwtService();
      ReflectionTestUtils.setField(jwtService, "secretKey", SECRET);
      filter = new JwtAuthenticationFilter(jwtService);
   }

   @AfterEach
   void clear() {
      SecurityContextHolder.clearContext();
   }

   private static String token(Object permissions) {
      return Jwts.builder()
            .subject("rosa@x.pe")
            .claim("userId", 20L)
            .claim("activeRole", "AUTORIDAD")
            .claim("roles", List.of("AUTORIDAD", "OPERADOR"))
            .claim("permissions", permissions)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 60_000))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();
   }

   private Authentication run(String authorization) throws Exception {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/shifts");
      if (authorization != null) {
         request.addHeader("Authorization", authorization);
      }
      filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
      return SecurityContextHolder.getContext().getAuthentication();
   }

   @Test
   @DisplayName("token válido: usuario con su ID, rol activo y los permisos como autoridades")
   void validToken() throws Exception {
      Authentication auth = run("Bearer " + token(List.of("shifts.read", "shifts.create")));

      assertThat(auth).isNotNull();
      CustomUserDetails user = (CustomUserDetails) auth.getPrincipal();
      assertThat(user.getId()).isEqualTo(20L);
      assertThat(user.getActiveRole()).isEqualTo("AUTORIDAD");
      assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
            .containsExactlyInAnyOrder("shifts.read", "shifts.create", "ROLE_AUTORIDAD");
   }

   @Test
   @DisplayName("los roles que no son el activo no suman autoridades")
   void onlyActiveRole() throws Exception {
      Authentication auth = run("Bearer " + token(List.of("shifts.read")));
      assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
            .doesNotContain("ROLE_OPERADOR");
   }

   @Test
   @DisplayName("sin token o con token falso: la petición sigue sin autenticar")
   void invalidToken() throws Exception {
      assertThat(run(null)).isNull();
      assertThat(run("Bearer token.falso.xyz")).isNull();
   }
}
