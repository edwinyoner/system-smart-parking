package com.winnersystems.smartparking.payment.application.port.input.paymenttype;

import com.winnersystems.smartparking.payment.application.dto.command.CreatePaymentTypeCommand;
import com.winnersystems.smartparking.payment.application.dto.query.PaymentTypeDto;

public interface CreatePaymentTypeUseCase {
   PaymentTypeDto createPaymentType(CreatePaymentTypeCommand command);
}
