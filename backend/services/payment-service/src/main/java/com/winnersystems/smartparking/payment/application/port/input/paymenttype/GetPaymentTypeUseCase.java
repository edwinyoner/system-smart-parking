package com.winnersystems.smartparking.payment.application.port.input.paymenttype;

import com.winnersystems.smartparking.payment.application.dto.query.PaymentTypeDto;

public interface GetPaymentTypeUseCase {
   PaymentTypeDto getPaymentTypeById(Long paymentTypeId);
}
