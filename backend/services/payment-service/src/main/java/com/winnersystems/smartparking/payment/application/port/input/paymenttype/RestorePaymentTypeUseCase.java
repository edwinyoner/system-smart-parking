package com.winnersystems.smartparking.payment.application.port.input.paymenttype;

import com.winnersystems.smartparking.payment.application.dto.query.PaymentTypeDto;

/** Saca un tipo de pago de la papelera; vuelve activo */
public interface RestorePaymentTypeUseCase {
   PaymentTypeDto restorePaymentType(Long paymentTypeId);
}
