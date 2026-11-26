package com.winnersystems.smartparking.payment.application.port.input.paymenttype;

import com.winnersystems.smartparking.payment.application.dto.query.PaymentTypeDto;

public interface TogglePaymentTypeStatusUseCase {
   PaymentTypeDto togglePaymentTypeStatus(Long paymentTypeId);
   PaymentTypeDto activatePaymentType(Long paymentTypeId);
   PaymentTypeDto deactivatePaymentType(Long paymentTypeId);
}
