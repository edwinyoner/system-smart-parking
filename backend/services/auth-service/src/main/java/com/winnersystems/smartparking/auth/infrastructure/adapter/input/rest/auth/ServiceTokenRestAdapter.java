package com.winnersystems.smartparking.auth.infrastructure.adapter.input.rest.auth;

import com.winnersystems.smartparking.auth.application.dto.command.IssueServiceTokenCommand;
import com.winnersystems.smartparking.auth.application.dto.query.ServiceTokenDto;
import com.winnersystems.smartparking.auth.application.port.input.auth.IssueServiceTokenUseCase;
import com.winnersystems.smartparking.auth.infrastructure.adapter.input.rest.auth.dto.request.ServiceTokenRequest;
import com.winnersystems.smartparking.auth.infrastructure.adapter.input.rest.auth.dto.response.ServiceTokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /service-token: token para llamadas entre microservicios (client credentials).
 *
 * Solo por la red interna: el gateway no publica esta ruta (responde 404 a /api/auth/service-token).
 */
@RestController
public class ServiceTokenRestAdapter {

   private final IssueServiceTokenUseCase issueServiceTokenUseCase;

   public ServiceTokenRestAdapter(IssueServiceTokenUseCase issueServiceTokenUseCase) {
      this.issueServiceTokenUseCase = issueServiceTokenUseCase;
   }

   @PostMapping("/service-token")
   public ResponseEntity<ServiceTokenResponse> issue(@Valid @RequestBody ServiceTokenRequest request) {
      ServiceTokenDto dto = issueServiceTokenUseCase.issueServiceToken(
            new IssueServiceTokenCommand(request.getClientId(), request.getClientSecret()));
      return ResponseEntity.ok(new ServiceTokenResponse(dto.accessToken(), "Bearer", dto.expiresInSeconds()));
   }
}
