package com.winnersystems.smartparking.payment.application.port.input.payment;

import com.winnersystems.smartparking.payment.application.dto.query.PaymentDto;
import com.winnersystems.smartparking.payment.domain.model.PaymentConcept;

import java.util.List;

public interface GetPaymentUseCase {
   PaymentDto getPaymentById(Long paymentId);

   /** Pagos de una estadía o una multa, el más reciente primero */
   List<PaymentDto> listPaymentsBySource(PaymentConcept concept, Long sourceId);
}
