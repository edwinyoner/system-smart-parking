package com.winnersystems.smartparking.payment.application.dto.command;

import java.math.BigDecimal;

/** Devolución manual, total o parcial */
public record RefundPaymentCommand(BigDecimal amount, String reason) {
}
