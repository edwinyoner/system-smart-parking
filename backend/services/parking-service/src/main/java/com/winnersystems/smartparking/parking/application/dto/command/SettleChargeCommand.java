package com.winnersystems.smartparking.parking.application.dto.command;

import com.winnersystems.smartparking.parking.domain.model.ChargeConcept;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Aviso de payment-service: estado actual de un pago de una estadía o una multa */
public record SettleChargeCommand(
      ChargeConcept concept,
      Long sourceId,
      Long paymentId,
      String status,
      BigDecimal amount,
      BigDecimal refundedAmount,
      LocalDateTime paidAt
) {
}
