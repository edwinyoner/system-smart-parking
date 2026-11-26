package com.winnersystems.smartparking.payment.application.port.input.payment;

import com.winnersystems.smartparking.payment.application.dto.command.RefundPaymentCommand;
import com.winnersystems.smartparking.payment.application.dto.query.PaymentDto;

/** Devolución manual: la registra quien entrega el dinero */
public interface RefundPaymentUseCase {
   PaymentDto refundPayment(Long paymentId, RefundPaymentCommand command);
}
