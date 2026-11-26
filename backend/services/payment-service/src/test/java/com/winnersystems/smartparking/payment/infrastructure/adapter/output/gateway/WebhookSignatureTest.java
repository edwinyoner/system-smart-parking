package com.winnersystems.smartparking.payment.infrastructure.adapter.output.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookSignatureTest {

   /** Secreto de prueba (solo para el test) */
   private final WebhookSignature signature = new WebhookSignature("secreto-de-prueba-para-el-webhook-0123456789");

   @Test
   @DisplayName("la firma del mismo cuerpo se acepta; otro cuerpo, otra clave o una firma rota, no")
   void verify() {
      String body = "{\"gatewayReference\":\"SIM-1\",\"event\":\"PAYMENT_SUCCEEDED\"}";
      String header = signature.sign(body);
      assertThat(header).startsWith("sha256=");
      assertThat(signature.verify(body, header)).isTrue();
      assertThat(signature.verify(body.replace("SIM-1", "SIM-2"), header)).isFalse();
      assertThat(new WebhookSignature("otro-secreto-distinto-para-el-webhook-98765").verify(body, header)).isFalse();
      assertThat(signature.verify(body, "sha256=zz")).isFalse();
      assertThat(signature.verify(body, null)).isFalse();
   }

   @Test
   @DisplayName("sin secreto (o corto) se rechaza todo y no se puede firmar")
   void notConfigured() {
      WebhookSignature empty = new WebhookSignature("");
      assertThat(empty.isConfigured()).isFalse();
      assertThat(empty.verify("{}", "sha256=00")).isFalse();
      assertThatThrownBy(() -> empty.sign("{}")).isInstanceOf(IllegalStateException.class);
      assertThat(new WebhookSignature("corto").isConfigured()).isFalse();
   }
}
