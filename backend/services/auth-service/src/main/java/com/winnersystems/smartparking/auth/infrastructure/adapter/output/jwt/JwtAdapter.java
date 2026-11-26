package com.winnersystems.smartparking.auth.infrastructure.adapter.output.jwt;

import com.winnersystems.smartparking.auth.application.port.output.JwtPort;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Component
public class JwtAdapter implements JwtPort {

   private final SecretKey secretKey;
   private final long accessTokenExpiration;
   private final long refreshTokenExpiration;
   private final long refreshTokenExpirationRememberMe;
   private final long serviceTokenExpiration;

   /** Claim que distingue un token de servicio de uno de usuario */
   public static final String TOKEN_TYPE_CLAIM = "tokenType";
   public static final String SERVICE_TOKEN_TYPE = "SERVICE";

   public JwtAdapter(
         @Value("${jwt.secret}") String secret,
         @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
         @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration,
         @Value("${jwt.refresh-token-expiration-remember-me}") long refreshTokenExpirationRememberMe,
         @Value("${jwt.service-token-expiration:300000}") long serviceTokenExpiration) {

      byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
      if (keyBytes.length < 32) {
         keyBytes = Base64.getUrlDecoder().decode(secret);
      }
      this.secretKey = Keys.hmacShaKeyFor(keyBytes);

      this.accessTokenExpiration = accessTokenExpiration;
      this.refreshTokenExpiration = refreshTokenExpiration;
      this.refreshTokenExpirationRememberMe = refreshTokenExpirationRememberMe;
      this.serviceTokenExpiration = serviceTokenExpiration;
   }

   @Override
   public String generateServiceToken(String clientId, Set<String> permissions) {
      Date now = new Date();
      return Jwts.builder()
            .claim(TOKEN_TYPE_CLAIM, SERVICE_TOKEN_TYPE)
            .claim("clientId", clientId)
            .claim("permissions", permissions)
            .subject("service:" + clientId)
            .issuedAt(now)
            .expiration(new Date(now.getTime() + serviceTokenExpiration))
            .signWith(secretKey)
            .compact();
   }

   @Override
   public long serviceTokenExpirationSeconds() {
      return serviceTokenExpiration / 1000;
   }

   @Override
   public boolean isServiceToken(String token) {
      return SERVICE_TOKEN_TYPE.equals(extractClaims(token).get(TOKEN_TYPE_CLAIM, String.class));
   }

   @Override
   public String generateAccessToken(
         Long userId,
         String email,
         String firstName,
         String lastName,
         String phoneNumber,
         String profilePicture,
         Boolean status,
         Boolean emailVerified,
         String activeRole,
         Set<String> roles,
         Set<String> permissions) {

      Map<String, Object> claims = new HashMap<>();

      claims.put("userId", userId);
      claims.put("email", email);
      claims.put("firstName", firstName);
      claims.put("lastName", lastName);
      claims.put("phoneNumber", phoneNumber);
      claims.put("profilePicture", profilePicture);
      claims.put("status", status);
      claims.put("emailVerified", emailVerified);

      // ROL ACTIVO (con el que inició sesión)
      claims.put("activeRole", activeRole);

      // TODOS LOS ROLES
      claims.put("roles", roles);
      claims.put("permissions", permissions);

      return Jwts.builder()
            .claims(claims)
            .subject(email)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
            .signWith(secretKey)
            .compact();
   }

   @Override
   public String extractEmail(String token) {
      return extractClaims(token).getSubject();
   }

   @Override
   public Long extractUserId(String token) {
      return extractClaims(token).get("userId", Long.class);
   }

   @Override
   public String extractActiveRole(String token) {
      return extractClaims(token).get("activeRole", String.class);
   }

   @Override
   public Instant extractIssuedAt(String token) {
      return extractClaims(token).getIssuedAt().toInstant();
   }

   @Override
   public boolean validateToken(String token) {
      try {
         extractClaims(token);
         return true;
      } catch (Exception e) {
         return false;
      }
   }

   private Claims extractClaims(String token) {
      return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
   }
}