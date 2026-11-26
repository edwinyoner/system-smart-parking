package com.winnersystems.smartparking.payment.application.port.input.paymenttype;

import com.winnersystems.smartparking.payment.application.dto.command.UpdatePaymentTypeCommand;
import com.winnersystems.smartparking.payment.application.dto.query.PaymentTypeDto;

public interface UpdatePaymentTypeUseCase {
   PaymentTypeDto updatePaymentType(Long paymentTypeId, UpdatePaymentTypeCommand command);
}
