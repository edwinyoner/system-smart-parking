package com.winnersystems.smartparking.payment.application.dto.command;

import java.time.LocalDateTime;

/**
 * Aviso de la pasarela (webhook) ya verificado: la firma se comprueba en el adaptador REST.
 *
 * @param succeeded true = pagado; false = rechazado
 */
public record GatewayNotification(
      String gatewayReference,
      boolean succeeded,
      String operationNumber,
      LocalDateTime paidAt,
      String failureReason
) {
}
