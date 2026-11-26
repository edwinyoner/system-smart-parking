package com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.paymenttype.mapper;

import com.winnersystems.smartparking.payment.application.dto.command.CreatePaymentTypeCommand;
import com.winnersystems.smartparking.payment.application.dto.command.UpdatePaymentTypeCommand;
import com.winnersystems.smartparking.payment.application.dto.query.PaymentTypeDto;
import com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.paymenttype.dto.request.CreatePaymentTypeRequest;
import com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.paymenttype.dto.request.UpdatePaymentTypeRequest;
import com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.paymenttype.dto.response.PaymentTypeResponse;
import org.springframework.stereotype.Component;

@Component
public class PaymentTypeRestMapper {

   public CreatePaymentTypeCommand toCommand(CreatePaymentTypeRequest r) {
      return new CreatePaymentTypeCommand(r.getCode(), r.getName(), r.getDescription(), r.getConfirmationMode(),
            Boolean.TRUE.equals(r.getRequiresReference()), r.getChannels(), r.getStatus());
   }

   public UpdatePaymentTypeCommand toCommand(UpdatePaymentTypeRequest r) {
      return new UpdatePaymentTypeCommand(r.getName(), r.getDescription(), r.getConfirmationMode(),
            Boolean.TRUE.equals(r.getRequiresReference()), r.getChannels(), r.getStatus());
   }

   public PaymentTypeResponse toResponse(PaymentTypeDto d) {
      return new PaymentTypeResponse(
            d.id(), d.code(), d.name(), d.description(), d.confirmationMode(), d.requiresReference(),
            d.channels().stream().sorted().toList(),
            d.status(), d.paymentsCount(), d.createdAt(), d.createdBy(), d.updatedAt(), d.updatedBy(), d.deletedAt(), d.deletedBy());
   }
}
