package com.winnersystems.smartparking.payment.infrastructure.adapter.output.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winnersystems.smartparking.payment.application.dto.query.GatewayCharge;
import com.winnersystems.smartparking.payment.application.port.output.PaymentGatewayPort;
import com.winnersystems.smartparking.payment.domain.exception.PaymentDomainException;
import com.winnersystems.smartparking.payment.domain.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pasarela simulada para la tesis (payment.gateway.mode = simulated).
 *
 * Se comporta como una real: crea cobros con QR dinámico que vencen, y cuando "el cliente paga"
 * envía el aviso al webhook por HTTP, firmado con el secreto compartido. Los cobros viven en memoria
 * (se pierden al reiniciar, igual que la base con create-drop).
 *
 * Una pasarela real (Niubiz, Culqi, Izipay...) implementaría PaymentGatewayPort y llamaría
 * al mismo webhook.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payment.gateway.mode", havingValue = "simulated", matchIfMissing = true)
public class SimulatedPaymentGateway implements PaymentGatewayPort {

   public enum ChargeState { PENDING, PAID, REJECTED, CANCELLED }

   /** Cobro guardado en la pasarela */
   public record SimulatedCharge(String reference, Long paymentId, BigDecimal amount, String description,
                                 LocalDateTime expiresAt, ChargeState state) {
      SimulatedCharge with(ChargeState newState) {
         return new SimulatedCharge(reference, paymentId, amount, description, expiresAt, newState);
      }
   }

   private final Map<String, SimulatedCharge> charges = new ConcurrentHashMap<>();
   private final WebhookSignature signature;
   private final ObjectMapper objectMapper;
   private final Clock clock;
   private final RestClient restClient;
   private final String webhookUrl;

   public SimulatedPaymentGateway(
         WebhookSignature signature,
         ObjectMapper objectMapper,
         Clock clock,
         @Value("${payment.gateway.simulator.webhook-url:http://localhost:8085/payments/webhook}") String webhookUrl) {
      this.signature = signature;
      this.objectMapper = objectMapper;
      this.clock = clock;
      this.webhookUrl = webhookUrl;
      this.restClient = RestClient.create();
   }

   // ========================= PaymentGatewayPort =========================

   @Override
   public GatewayCharge createQrCharge(Long paymentId, BigDecimal amount, String description, LocalDateTime expiresAt) {
      String reference = "SIM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
      charges.put(reference, new SimulatedCharge(reference, paymentId, amount, description, expiresAt, ChargeState.PENDING));
      // Contenido del QR: en una pasarela real sería una cadena EMVCo que leen Yape, Plin y la banca
      String qr = "SMARTPARKING-SIM|" + reference + "|PEN|" + amount.toPlainString();
      log.debug("Pasarela simulada: cobro {} por S/ {}", reference, amount);
      return new GatewayCharge(reference, qr, expiresAt);
   }

   @Override
   public void cancelCharge(String gatewayReference) {
      charges.computeIfPresent(gatewayReference,
            (ref, c) -> c.state() == ChargeState.PENDING ? c.with(ChargeState.CANCELLED) : c);
   }

   // ========================= Simulación del cliente =========================

   public SimulatedCharge getCharge(String reference) {
      SimulatedCharge charge = charges.get(reference);
      if (charge == null) {
         throw new ResourceNotFoundException("La pasarela simulada no tiene el cobro " + reference);
      }
      return charge;
   }

   /** El cliente escaneó el QR y pagó: la pasarela avisa al webhook */
   public SimulatedCharge pay(String reference) {
      SimulatedCharge charge = requirePayable(reference);
      String operation = "OP" + System.currentTimeMillis() % 1_000_000_000L;
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("gatewayReference", reference);
      body.put("event", "PAYMENT_SUCCEEDED");
      body.put("operationNumber", operation);
      body.put("paidAt", LocalDateTime.now(clock).toString());
      sendWebhook(body);
      SimulatedCharge paid = charge.with(ChargeState.PAID);
      charges.put(reference, paid);
      return paid;
   }

   /** El banco rechazó el pago (por ejemplo, saldo insuficiente) */
   public SimulatedCharge reject(String reference, String reason) {
      SimulatedCharge charge = requirePayable(reference);
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("gatewayReference", reference);
      body.put("event", "PAYMENT_FAILED");
      body.put("reason", reason == null || reason.isBlank() ? "Rechazado por el banco (simulado)" : reason);
      sendWebhook(body);
      SimulatedCharge rejected = charge.with(ChargeState.REJECTED);
      charges.put(reference, rejected);
      return rejected;
   }

   private SimulatedCharge requirePayable(String reference) {
      SimulatedCharge charge = getCharge(reference);
      if (charge.state() != ChargeState.PENDING) {
         throw new PaymentDomainException("El cobro " + reference + " está " + charge.state() + ": ya no acepta pagos");
      }
      if (!LocalDateTime.now(clock).isBefore(charge.expiresAt())) {
         throw new PaymentDomainException("El QR " + reference + " venció");
      }
      return charge;
   }

   private void sendWebhook(Map<String, Object> body) {
      try {
         String json = objectMapper.writeValueAsString(body);
         restClient.post()
               .uri(webhookUrl)
               .contentType(MediaType.APPLICATION_JSON)
               .header(WebhookSignature.HEADER, signature.sign(json))
               .body(json)
               .retrieve()
               .toBodilessEntity();
      } catch (Exception e) {
         throw new PaymentDomainException("La pasarela simulada no pudo avisar al webhook: " + e.getMessage());
      }
   }
}
