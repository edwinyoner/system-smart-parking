package com.winnersystems.smartparking.parking.infrastructure.adapter.output.client;

import com.winnersystems.smartparking.parking.application.port.output.UserDirectoryPort;
import feign.FeignException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Consulta usuarios en auth-service con el token del usuario actual
 * (FeignAuthConfig lo reenvía), así que auth-service aplica sus propios permisos.
 */
@Component
public class AuthUserDirectoryAdapter implements UserDirectoryPort {

   private final AuthServiceClient authServiceClient;

   public AuthUserDirectoryAdapter(AuthServiceClient authServiceClient) {
      this.authServiceClient = authServiceClient;
   }

   @Override
   public Optional<UserSummary> findUser(Long userId) {
      if (userId == null) {
         return Optional.empty();
      }
      try {
         AuthServiceClient.UserDto user = authServiceClient.getUserById(userId);
         if (user == null) {
            return Optional.empty();
         }
         return Optional.of(new UserSummary(user.id(), user.getFullName(), user.email(),
               Boolean.TRUE.equals(user.status())));
      } catch (FeignException.NotFound e) {
         return Optional.empty();
      } catch (FeignException.Forbidden | FeignException.Unauthorized e) {
         throw new AccessDeniedException("No tienes permiso para consultar usuarios (users.read)");
      } catch (FeignException e) {
         throw new UserDirectoryUnavailableException(
               "No se pudo consultar el usuario en auth-service. Inténtalo de nuevo en unos segundos.", e);
      }
   }
}
