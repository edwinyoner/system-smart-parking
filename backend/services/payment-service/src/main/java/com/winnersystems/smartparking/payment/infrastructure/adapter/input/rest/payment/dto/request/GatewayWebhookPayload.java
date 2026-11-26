package com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Cuerpo del aviso de la pasarela.
 *
 * @param event PAYMENT_SUCCEEDED o PAYMENT_FAILED
 * @param paidAt ISO-8601 local (America/Lima)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GatewayWebhookPayload(
      String gatewayReference,
      String event,
      String operationNumber,
      String paidAt,
      String reason
) {
}
