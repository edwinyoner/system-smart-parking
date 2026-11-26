package com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winnersystems.smartparking.payment.application.dto.command.GatewayNotification;
import com.winnersystems.smartparking.payment.application.port.input.payment.HandleGatewayNotificationUseCase;
import com.winnersystems.smartparking.payment.infrastructure.adapter.output.gateway.WebhookSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GatewayWebhookRestAdapterTest {

   private final HandleGatewayNotificationUseCase useCase = mock(HandleGatewayNotificationUseCase.class);
   /** Secreto de prueba (solo para el test) */
   private final WebhookSignature signature = new WebhookSignature("secreto-de-prueba-para-el-webhook-0123456789");
   private final GatewayWebhookRestAdapter adapter = new GatewayWebhookRestAdapter(useCase, signature, new ObjectMapper());

   private static final String PAID = """
         {"gatewayReference":"SIM-1","event":"PAYMENT_SUCCEEDED","operationNumber":"OP9","paidAt":"2026-11-25T10:00:00"}""";

   @Test
   @DisplayName("firma válida: procesa el pago con sus datos")
   void valid() {
      assertThat(adapter.receive(PAID, signature.sign(PAID)).getStatusCode().value()).isEqualTo(200);
      ArgumentCaptor<GatewayNotification> captor = ArgumentCaptor.forClass(GatewayNotification.class);
      verify(useCase).handleGatewayNotification(captor.capture());
      assertThat(captor.getValue().succeeded()).isTrue();
      assertThat(captor.getValue().operationNumber()).isEqualTo("OP9");
      assertThat(captor.getValue().paidAt()).isEqualTo(LocalDateTime.of(2026, 11, 25, 10, 0));
   }

   @Test
   @DisplayName("sin firma, con firma de otro cuerpo o alterado: 401 y no se procesa")
   void invalid() {
      assertThat(adapter.receive(PAID, null).getStatusCode().value()).isEqualTo(401);
      String altered = PAID.replace("SIM-1", "SIM-2");
      assertThat(adapter.receive(altered, signature.sign(PAID)).getStatusCode().value()).isEqualTo(401);
      verify(useCase, never()).handleGatewayNotification(any());
   }

   @Test
   @DisplayName("evento desconocido: 200 sin procesar")
   void unknownEvent() {
      String body = "{\"gatewayReference\":\"SIM-1\",\"event\":\"CHARGE_CREATED\"}";
      assertThat(adapter.receive(body, signature.sign(body)).getStatusCode().value()).isEqualTo(200);
      verify(useCase, never()).handleGatewayNotification(any());
   }
}
