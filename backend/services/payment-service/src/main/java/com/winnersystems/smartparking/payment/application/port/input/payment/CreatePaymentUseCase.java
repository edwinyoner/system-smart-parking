package com.winnersystems.smartparking.payment.application.port.input.payment;

import com.winnersystems.smartparking.payment.application.dto.command.CreatePaymentCommand;
import com.winnersystems.smartparking.payment.application.dto.query.PaymentDto;

public interface CreatePaymentUseCase {
   /** Efectivo: queda COMPLETED. QR: queda PENDING con el QR para mostrar */
   PaymentDto createPayment(CreatePaymentCommand command);
}
