package com.winnersystems.smartparking.payment.application.port.input.payment;

import com.winnersystems.smartparking.payment.application.dto.query.PaymentDto;

/** Anula un pago PENDING (un QR que no se pagó) */
public interface CancelPaymentUseCase {
   PaymentDto cancelPayment(Long paymentId, String reason);
}
