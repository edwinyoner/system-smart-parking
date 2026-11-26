package com.winnersystems.smartparking.auth.application.service.auth;

import com.winnersystems.smartparking.auth.application.dto.command.IssueServiceTokenCommand;
import com.winnersystems.smartparking.auth.application.dto.query.ServiceTokenDto;
import com.winnersystems.smartparking.auth.application.port.output.JwtPort;
import com.winnersystems.smartparking.auth.application.port.output.ServiceClientPort;
import com.winnersystems.smartparking.auth.domain.exception.InvalidCredentialsException;
import com.winnersystems.smartparking.auth.domain.model.ServiceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ServiceTokenServiceTest {

   private final ServiceClientPort clients = mock(ServiceClientPort.class);
   private final JwtPort jwt = mock(JwtPort.class);
   private final ServiceTokenService service = new ServiceTokenService(clients, jwt);

   @Test
   @DisplayName("credenciales válidas: emite el token con los permisos del cliente")
   void issue() {
      Set<String> perms = Set.of("internal.charges.read");
      when(clients.authenticate("payment-service", "s")).thenReturn(Optional.of(new ServiceClient("payment-service", perms)));
      when(jwt.generateServiceToken("payment-service", perms)).thenReturn("tok");
      when(jwt.serviceTokenExpirationSeconds()).thenReturn(300L);

      ServiceTokenDto dto = service.issueServiceToken(new IssueServiceTokenCommand("payment-service", "s"));
      assertThat(dto.accessToken()).isEqualTo("tok");
      assertThat(dto.expiresInSeconds()).isEqualTo(300);
   }

   @Test
   @DisplayName("credenciales inválidas: 401 y no se emite token")
   void reject() {
      when(clients.authenticate(any(), any())).thenReturn(Optional.empty());
      assertThatThrownBy(() -> service.issueServiceToken(new IssueServiceTokenCommand("payment-service", "x")))
            .isInstanceOf(InvalidCredentialsException.class);
      verify(jwt, never()).generateServiceToken(any(), any());
   }
}
