package com.winnersystems.smartparking.auth.domain.model;

import java.util.Objects;
import java.util.Set;

/**
 * Microservicio que pide un token propio para llamar a otro (client credentials).
 *
 * No es un usuario: no inicia sesión, no tiene roles y sus permisos son internos (prefijo internal.),
 * que no existen en la tabla de permisos y por eso ningún rol humano puede recibirlos.
 * Ejemplo: payment-service avisa a parking-service que una estadía quedó pagada.
 */
public class ServiceClient {

   public static final String INTERNAL_PREFIX = "internal.";

   private final String clientId;
   private final Set<String> permissions;

   public ServiceClient(String clientId, Set<String> permissions) {
      if (clientId == null || clientId.isBlank()) {
         throw new IllegalArgumentException("El cliente de servicio necesita un identificador");
      }
      Set<String> perms = permissions == null ? Set.of() : Set.copyOf(permissions);
      // Un cliente de servicio solo recibe permisos internos: así nunca suplanta a un usuario
      perms.stream()
            .filter(p -> !p.startsWith(INTERNAL_PREFIX))
            .findAny()
            .ifPresent(p -> {
               throw new IllegalArgumentException("Permiso no interno para " + clientId + ": " + p);
            });
      this.clientId = clientId;
      this.permissions = perms;
   }

   public String getClientId() { return clientId; }

   public Set<String> getPermissions() { return permissions; }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      return clientId.equals(((ServiceClient) o).clientId);
   }

   @Override
   public int hashCode() {
      return Objects.hash(clientId);
   }

   @Override
   public String toString() {
      return "ServiceClient{" + clientId + ", permisos=" + permissions + '}';
   }
}
