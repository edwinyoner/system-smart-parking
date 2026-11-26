package com.winnersystems.smartparking.auth.infrastructure.adapter.output.serviceclient;

import com.winnersystems.smartparking.auth.application.port.output.ServiceClientPort;
import com.winnersystems.smartparking.auth.domain.model.ServiceClient;
import com.winnersystems.smartparking.auth.infrastructure.config.ServiceClientProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Optional;

/**
 * Clientes de servicio leídos de la configuración. Los secretos vienen de backend/.env.
 *
 * Un cliente sin secreto, o con uno de menos de 32 caracteres, queda deshabilitado:
 * así un .env incompleto no abre la puerta, solo impide pedir el token.
 */
@Slf4j
@Component
@EnableConfigurationProperties(ServiceClientProperties.class)
public class ServiceClientAdapter implements ServiceClientPort {

   static final int MIN_SECRET_LENGTH = 32;

   private final ServiceClientProperties properties;

   public ServiceClientAdapter(ServiceClientProperties properties) {
      this.properties = properties;
      properties.getClients().forEach((id, client) -> {
         if (!isUsable(client)) {
            log.warn("Cliente de servicio {} deshabilitado: falta su secreto (mínimo {} caracteres)", id, MIN_SECRET_LENGTH);
         }
      });
   }

   @Override
   public Optional<ServiceClient> authenticate(String clientId, String clientSecret) {
      if (clientId == null || clientSecret == null) {
         return Optional.empty();
      }
      ServiceClientProperties.Client client = properties.getClients().get(clientId);
      if (client == null || !isUsable(client)) {
         return Optional.empty();
      }
      // Comparación en tiempo constante: no revela cuántos caracteres coinciden
      boolean matches = MessageDigest.isEqual(
            client.getSecret().getBytes(StandardCharsets.UTF_8),
            clientSecret.getBytes(StandardCharsets.UTF_8));
      return matches
            ? Optional.of(new ServiceClient(clientId, new HashSet<>(client.getPermissions())))
            : Optional.empty();
   }

   private static boolean isUsable(ServiceClientProperties.Client client) {
      return client.getSecret() != null && client.getSecret().length() >= MIN_SECRET_LENGTH;
   }
}
