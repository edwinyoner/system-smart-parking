package com.winnersystems.smartparking.parking.infrastructure.adapter.output.client;

import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthUserDirectoryAdapterTest {

   private static final Request REQUEST = Request.create(Request.HttpMethod.GET, "/users/5", Map.of(),
         null, StandardCharsets.UTF_8, null);

   private static FeignException error(int status) {
      return FeignException.errorStatus("getUserById",
            feign.Response.builder().status(status).reason("x").request(REQUEST).headers(Map.of()).build());
   }

   @Test
   @DisplayName("usuario encontrado, no encontrado (vacío), sin permiso (403) y auth caído (503)")
   void mapsResponses() {
      AuthServiceClient client = mock(AuthServiceClient.class);
      AuthUserDirectoryAdapter adapter = new AuthUserDirectoryAdapter(client);

      when(client.getUserById(5L)).thenReturn(new AuthServiceClient.UserDto(
            5L, "Ana", "Ríos", null, "a@x.pe", null, null, true, true));
      assertThat(adapter.findUser(5L)).hasValueSatisfying(u -> {
         assertThat(u.fullName()).isEqualTo("Ana Ríos");
         assertThat(u.active()).isTrue();
      });

      when(client.getUserById(5L)).thenThrow(error(404));
      assertThat(adapter.findUser(5L)).isEmpty();

      when(client.getUserById(6L)).thenThrow(error(403));
      assertThatThrownBy(() -> adapter.findUser(6L)).isInstanceOf(AccessDeniedException.class);

      when(client.getUserById(7L)).thenThrow(error(503));
      assertThatThrownBy(() -> adapter.findUser(7L)).isInstanceOf(UserDirectoryUnavailableException.class);

      assertThat(adapter.findUser(null)).isEmpty();
   }
}
