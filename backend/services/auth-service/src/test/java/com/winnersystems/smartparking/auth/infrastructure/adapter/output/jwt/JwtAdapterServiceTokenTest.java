package com.winnersystems.smartparking.auth.infrastructure.adapter.output.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAdapterServiceTokenTest {

   /** Secreto de prueba (solo para el test) */
   private static final String SECRET = "clave-de-prueba-solo-para-tests-0123456789-abcdefghij";

   private final JwtAdapter jwt = new JwtAdapter(SECRET, 1_800_000, 604_800_000, 2_592_000_000L, 300_000);

   @Test
   @DisplayName("token de servicio: tokenType SERVICE, sin userId, con permisos y 5 minutos")
   void serviceToken() {
      String token = jwt.generateServiceToken("payment-service", Set.of("internal.charges.settle"));
      Claims claims = Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .build().parseSignedClaims(token).getPayload();

      assertThat(claims.getSubject()).isEqualTo("service:payment-service");
      assertThat(claims.get("tokenType")).isEqualTo("SERVICE");
      assertThat(claims.get("userId")).isNull();
      assertThat(claims.get("permissions").toString()).isEqualTo("[internal.charges.settle]");
      assertThat(jwt.isServiceToken(token)).isTrue();
      assertThat(jwt.serviceTokenExpirationSeconds()).isEqualTo(300);
      long ttl = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
      assertThat(ttl).isEqualTo(300_000);
   }

   @Test
   @DisplayName("un token de usuario no es de servicio")
   void userToken() {
      String token = jwt.generateAccessToken(1L, "a@b.pe", "A", "B", null, null, true, true,
            "ADMIN", Set.of("ADMIN"), Set.of("users.read"));
      assertThat(jwt.isServiceToken(token)).isFalse();
   }
}
