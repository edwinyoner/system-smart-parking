package com.winnersystems.smartparking.auth.infrastructure.adapter.output.serviceclient;

import com.winnersystems.smartparking.auth.domain.model.ServiceClient;
import com.winnersystems.smartparking.auth.infrastructure.config.ServiceClientProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceClientAdapterTest {

   /** Secreto de prueba (solo para el test) */
   private static final String SECRET = "secreto-de-prueba-de-32-caracteres-o-mas";

   private static ServiceClientAdapter adapter(String secret, List<String> permissions) {
      ServiceClientProperties.Client client = new ServiceClientProperties.Client();
      client.setSecret(secret);
      client.setPermissions(permissions);
      ServiceClientProperties props = new ServiceClientProperties();
      props.setClients(Map.of("payment-service", client));
      return new ServiceClientAdapter(props);
   }

   @Test
   @DisplayName("secreto correcto: devuelve el cliente con sus permisos internos")
   void valid() {
      ServiceClient client = adapter(SECRET, List.of("internal.charges.settle"))
            .authenticate("payment-service", SECRET).orElseThrow();
      assertThat(client.getPermissions()).containsExactly("internal.charges.settle");
   }

   @Test
   @DisplayName("secreto incorrecto, cliente desconocido o datos nulos: vacío")
   void invalid() {
      ServiceClientAdapter adapter = adapter(SECRET, List.of());
      assertThat(adapter.authenticate("payment-service", SECRET + "x")).isEmpty();
      assertThat(adapter.authenticate("otro-service", SECRET)).isEmpty();
      assertThat(adapter.authenticate(null, SECRET)).isEmpty();
      assertThat(adapter.authenticate("payment-service", null)).isEmpty();
   }

   @Test
   @DisplayName("sin secreto o con uno corto el cliente queda deshabilitado")
   void disabled() {
      assertThat(adapter("", List.of()).authenticate("payment-service", "")).isEmpty();
      assertThat(adapter("corto", List.of()).authenticate("payment-service", "corto")).isEmpty();
      assertThat(adapter(null, List.of()).authenticate("payment-service", "x")).isEmpty();
   }

   @Test
   @DisplayName("un permiso no interno en la configuración se rechaza")
   void onlyInternal() {
      assertThatThrownBy(() -> adapter(SECRET, List.of("users.delete")).authenticate("payment-service", SECRET))
            .isInstanceOf(IllegalArgumentException.class);
   }
}
