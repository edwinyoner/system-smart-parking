package com.winnersystems.smartparking.payment.infrastructure.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Valida los JWT que emite auth-service. No genera tokens.
 * La firma usa los bytes UTF-8 del secreto, igual que auth-service.
 */
@Service
public class JwtService {

   @Value("${jwt.secret}")
   private String secretKey;

   /** Claims de un token con firma válida y no vencido (jjwt rechaza los vencidos) */
   public Claims extractAllClaims(String token) {
      return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
   }

   public boolean validateToken(String token) {
      try {
         extractAllClaims(token);
         return true;
      } catch (Exception e) {
         return false;
      }
   }

   private SecretKey getSigningKey() {
      return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
   }
}
