package com.winnersystems.smartparking.payment.application.port.input.paymenttype;

import com.winnersystems.smartparking.payment.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.payment.application.dto.query.PaymentTypeDto;
import com.winnersystems.smartparking.payment.domain.model.PaymentChannel;

import java.util.List;

public interface ListPaymentTypesUseCase {

   /** deleted = true lista la papelera */
   PagedResponse<PaymentTypeDto> listAllPaymentTypes(int page, int size, String search, Boolean status, boolean deleted);

   /** Activos; con channel, solo los que se ofrecen en ese canal (null = todos) */
   List<PaymentTypeDto> listActivePaymentTypes(PaymentChannel channel);
}
