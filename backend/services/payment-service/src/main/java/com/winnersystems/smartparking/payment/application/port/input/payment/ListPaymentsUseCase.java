package com.winnersystems.smartparking.payment.application.port.input.payment;

import com.winnersystems.smartparking.payment.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.payment.application.dto.query.PaymentDto;
import com.winnersystems.smartparking.payment.application.dto.query.PaymentSearchCriteria;

public interface ListPaymentsUseCase {
   PagedResponse<PaymentDto> listPayments(PaymentSearchCriteria criteria, int page, int size);
}
