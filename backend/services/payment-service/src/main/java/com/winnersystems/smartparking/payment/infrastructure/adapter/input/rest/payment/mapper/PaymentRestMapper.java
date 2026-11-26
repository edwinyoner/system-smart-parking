package com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.payment.mapper;

import com.winnersystems.smartparking.payment.application.dto.command.CreatePaymentCommand;
import com.winnersystems.smartparking.payment.application.dto.query.PaymentDto;
import com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.payment.dto.request.CreatePaymentRequest;
import com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.payment.dto.response.PaymentResponse;
import org.springframework.stereotype.Component;

@Component
public class PaymentRestMapper {

   public CreatePaymentCommand toCommand(CreatePaymentRequest r) {
      return new CreatePaymentCommand(r.getConcept(), r.getSourceId(), r.getPaymentTypeId(), r.getChannel(),
            r.getOperationNumber());
   }

   public PaymentResponse toResponse(PaymentDto d) {
      return new PaymentResponse(
            d.id(), d.concept(), d.sourceId(), d.description(), d.plateNumber(), d.paymentTypeId(),
            d.paymentTypeCode(), d.paymentTypeName(), d.confirmationMode(), d.channel(), d.amount(), d.currency(),
            d.status(), d.gatewayReference(), d.qrPayload(), d.expiresAt(), d.operationNumber(), d.operatorId(),
            d.paidAt(), d.failureReason(), d.cancelReason(), d.refundedAmount(), d.refundableAmount(),
            d.refundReason(), d.refundedAt(), d.refundedBy(), d.settlementNotified(), d.createdAt(), d.createdBy(),
            d.updatedAt(), d.updatedBy());
   }
}
