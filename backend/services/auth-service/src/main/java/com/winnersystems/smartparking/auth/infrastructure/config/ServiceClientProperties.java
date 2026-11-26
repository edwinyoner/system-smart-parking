package com.winnersystems.smartparking.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Microservicios que pueden pedir un token de servicio (auth-service.yml):
 *
 * <pre>
 * service-clients:
 *   clients:
 *     payment-service:
 *       secret: ${PAYMENT_SERVICE_CLIENT_SECRET:}
 *       permissions: [internal.charges.read, internal.charges.settle]
 * </pre>
 */
@ConfigurationProperties(prefix = "service-clients")
public class ServiceClientProperties {

   private Map<String, Client> clients = new HashMap<>();

   public Map<String, Client> getClients() { return clients; }
   public void setClients(Map<String, Client> clients) { this.clients = clients; }

   public static class Client {
      private String secret;
      private List<String> permissions = List.of();

      public String getSecret() { return secret; }
      public void setSecret(String secret) { this.secret = secret; }

      public List<String> getPermissions() { return permissions; }
      public void setPermissions(List<String> permissions) { this.permissions = permissions; }
   }
}
