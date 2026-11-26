package com.winnersystems.smartparking.payment.infrastructure.adapter.output.client;

import com.winnersystems.smartparking.payment.domain.exception.ExternalServiceException;
import com.winnersystems.smartparking.payment.infrastructure.adapter.output.client.dto.ServiceTokenRequest;
import com.winnersystems.smartparking.payment.infrastructure.adapter.output.client.dto.ServiceTokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Token de servicio de payment-service (client credentials ante auth-service).
 *
 * Se guarda en memoria y se pide uno nuevo 30 s antes de que venza, así no se pide en cada llamada.
 * El secreto viene de backend/.env (PAYMENT_SERVICE_CLIENT_SECRET) y nunca se registra en el log.
 */
@Slf4j
@Component
public class ServiceTokenProvider {

   private static final long RENEW_BEFORE_SECONDS = 30;

   private final AuthServiceClient authServiceClient;
   private final Clock clock;
   private final String clientId;
   private final String clientSecret;

   private String token;
   private Instant expiresAt = Instant.EPOCH;

   public ServiceTokenProvider(
         AuthServiceClient authServiceClient,
         Clock clock,
         @Value("${service-client.client-id:payment-service}") String clientId,
         @Value("${service-client.secret:}") String clientSecret) {
      this.authServiceClient = authServiceClient;
      this.clock = clock;
      this.clientId = clientId;
      this.clientSecret = clientSecret;
      if (clientSecret.isBlank()) {
         log.warn("Falta PAYMENT_SERVICE_CLIENT_SECRET: payment-service no podrá llamar a parking-service");
      }
   }

   public synchronized String getToken() {
      Instant now = clock.instant();
      if (token == null || now.isAfter(expiresAt.minusSeconds(RENEW_BEFORE_SECONDS))) {
         if (clientSecret.isBlank()) {
            throw new ExternalServiceException("Falta configurar el secreto del cliente de servicio", null);
         }
         try {
            ServiceTokenResponse response = authServiceClient.issueServiceToken(
                  new ServiceTokenRequest(clientId, clientSecret));
            token = response.accessToken();
            expiresAt = now.plusSeconds(response.expiresIn());
            log.debug("Token de servicio renovado; vence en {} s", response.expiresIn());
         } catch (RuntimeException e) {
            throw new ExternalServiceException("auth-service no entregó el token de servicio", e);
         }
      }
      return token;
   }

   /** Olvida el token (por ejemplo, si parking-service respondió 401) */
   public synchronized void invalidate() {
      token = null;
   }
}
