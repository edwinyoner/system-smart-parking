package com.winnersystems.smartparking.auth.application.service.auth;

import com.winnersystems.smartparking.auth.application.dto.command.IssueServiceTokenCommand;
import com.winnersystems.smartparking.auth.application.dto.query.ServiceTokenDto;
import com.winnersystems.smartparking.auth.application.port.input.auth.IssueServiceTokenUseCase;
import com.winnersystems.smartparking.auth.application.port.output.JwtPort;
import com.winnersystems.smartparking.auth.application.port.output.ServiceClientPort;
import com.winnersystems.smartparking.auth.domain.exception.InvalidCredentialsException;
import com.winnersystems.smartparking.auth.domain.model.ServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Tokens de servicio (client credentials).
 *
 * El token dura poco (jwt.service-token-expiration), lleva tokenType = SERVICE, no tiene userId
 * y solo trae permisos internos. El microservicio que lo pide lo guarda y lo renueva al vencer.
 */
@Slf4j
@Service
public class ServiceTokenService implements IssueServiceTokenUseCase {

   private final ServiceClientPort serviceClientPort;
   private final JwtPort jwtPort;

   public ServiceTokenService(ServiceClientPort serviceClientPort, JwtPort jwtPort) {
      this.serviceClientPort = serviceClientPort;
      this.jwtPort = jwtPort;
   }

   @Override
   public ServiceTokenDto issueServiceToken(IssueServiceTokenCommand command) {
      ServiceClient client = serviceClientPort.authenticate(command.clientId(), command.clientSecret())
            .orElseThrow(() -> {
               // No se registra el secreto, solo el cliente que lo intentó
               log.warn("Token de servicio rechazado para el cliente {}", command.clientId());
               return new InvalidCredentialsException("Credenciales de servicio inválidas");
            });

      String token = jwtPort.generateServiceToken(client.getClientId(), client.getPermissions());
      log.debug("Token de servicio emitido para {}", client.getClientId());
      return new ServiceTokenDto(token, jwtPort.serviceTokenExpirationSeconds());
   }
}
