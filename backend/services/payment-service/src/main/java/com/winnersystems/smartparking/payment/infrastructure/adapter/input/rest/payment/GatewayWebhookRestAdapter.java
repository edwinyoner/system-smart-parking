package com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winnersystems.smartparking.payment.application.dto.command.GatewayNotification;
import com.winnersystems.smartparking.payment.application.port.input.payment.HandleGatewayNotificationUseCase;
import com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.payment.dto.request.GatewayWebhookPayload;
import com.winnersystems.smartparking.payment.infrastructure.adapter.output.gateway.WebhookSignature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * POST /payments/webhook: avisos de la pasarela. Es público (la pasarela no tiene JWT), así que
 * la seguridad es la firma HMAC del cuerpo: sin firma válida responde 401 y no cambia nada.
 *
 * El cuerpo se lee como texto para verificar la firma sobre los bytes exactos que llegaron.
 */
@Slf4j
@RestController
@RequestMapping("/payments/webhook")
public class GatewayWebhookRestAdapter {

   private final HandleGatewayNotificationUseCase useCase;
   private final WebhookSignature signature;
   private final ObjectMapper objectMapper;

   public GatewayWebhookRestAdapter(HandleGatewayNotificationUseCase useCase, WebhookSignature signature,
                                    ObjectMapper objectMapper) {
      this.useCase = useCase;
      this.signature = signature;
      this.objectMapper = objectMapper;
   }

   @PostMapping
   public ResponseEntity<Void> receive(@RequestBody String body,
                                       @RequestHeader(value = WebhookSignature.HEADER, required = false) String header) {
      if (!signature.verify(body, header)) {
         log.warn("Aviso de la pasarela con firma inválida: rechazado");
         return ResponseEntity.status(401).build();
      }
      GatewayWebhookPayload payload;
      try {
         payload = objectMapper.readValue(body, GatewayWebhookPayload.class);
      } catch (Exception e) {
         return ResponseEntity.badRequest().build();
      }
      boolean succeeded = "PAYMENT_SUCCEEDED".equals(payload.event());
      if (!succeeded && !"PAYMENT_FAILED".equals(payload.event())) {
         log.debug("Evento de la pasarela ignorado: {}", payload.event());
         return ResponseEntity.ok().build();
      }
      LocalDateTime paidAt = payload.paidAt() == null ? null : LocalDateTime.parse(payload.paidAt());
      useCase.handleGatewayNotification(new GatewayNotification(
            payload.gatewayReference(), succeeded, payload.operationNumber(), paidAt, payload.reason()));
      return ResponseEntity.ok().build();
   }
}
