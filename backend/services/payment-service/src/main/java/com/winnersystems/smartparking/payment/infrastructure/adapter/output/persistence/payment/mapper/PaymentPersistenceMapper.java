package com.winnersystems.smartparking.payment.infrastructure.adapter.output.persistence.payment.mapper;

import com.winnersystems.smartparking.payment.domain.model.Payment;
import com.winnersystems.smartparking.payment.infrastructure.adapter.output.persistence.payment.entity.PaymentEntity;
import org.springframework.stereotype.Component;

@Component
public class PaymentPersistenceMapper {

   public Payment toDomain(PaymentEntity e) {
      if (e == null) return null;
      Payment p = new Payment();
      p.setId(e.getId());
      p.setConcept(e.getConcept());
      p.setSourceId(e.getSourceId());
      p.setDescription(e.getDescription());
      p.setPlateNumber(e.getPlateNumber());
      p.setPaymentTypeId(e.getPaymentTypeId());
      p.setPaymentTypeCode(e.getPaymentTypeCode());
      p.setPaymentTypeName(e.getPaymentTypeName());
      p.setConfirmationMode(e.getConfirmationMode());
      p.setChannel(e.getChannel());
      p.setAmount(e.getAmount());
      p.setCurrency(e.getCurrency());
      p.setStatus(e.getStatus());
      p.setGatewayReference(e.getGatewayReference());
      p.setQrPayload(e.getQrPayload());
      p.setExpiresAt(e.getExpiresAt());
      p.setOperationNumber(e.getOperationNumber());
      p.setOperatorId(e.getOperatorId());
      p.setPaidAt(e.getPaidAt());
      p.setFailureReason(e.getFailureReason());
      p.setCancelReason(e.getCancelReason());
      p.setRefundedAmount(e.getRefundedAmount());
      p.setRefundReason(e.getRefundReason());
      p.setRefundedAt(e.getRefundedAt());
      p.setRefundedBy(e.getRefundedBy());
      p.setSettlementNotified(Boolean.TRUE.equals(e.getSettlementNotified()));
      p.setCreatedAt(e.getCreatedAt());
      p.setCreatedBy(e.getCreatedBy());
      p.setUpdatedAt(e.getUpdatedAt());
      p.setUpdatedBy(e.getUpdatedBy());
      return p;
   }

   /** Copia el dominio sobre una entidad nueva o existente (lo de la creación solo al crear) */
   public void copyToEntity(Payment p, PaymentEntity e) {
      if (e.getId() == null) {
         e.setConcept(p.getConcept());
         e.setSourceId(p.getSourceId());
         e.setPaymentTypeId(p.getPaymentTypeId());
         e.setPaymentTypeCode(p.getPaymentTypeCode());
         e.setPaymentTypeName(p.getPaymentTypeName());
         e.setConfirmationMode(p.getConfirmationMode());
         e.setChannel(p.getChannel());
         e.setAmount(p.getAmount());
         e.setCurrency(p.getCurrency());
         e.setCreatedAt(p.getCreatedAt());
         e.setCreatedBy(p.getCreatedBy());
      }
      e.setDescription(p.getDescription());
      e.setPlateNumber(p.getPlateNumber());
      e.setStatus(p.getStatus());
      e.setGatewayReference(p.getGatewayReference());
      e.setQrPayload(p.getQrPayload());
      e.setExpiresAt(p.getExpiresAt());
      e.setOperationNumber(p.getOperationNumber());
      e.setOperatorId(p.getOperatorId());
      e.setPaidAt(p.getPaidAt());
      e.setFailureReason(p.getFailureReason());
      e.setCancelReason(p.getCancelReason());
      e.setRefundedAmount(p.getRefundedAmount());
      e.setRefundReason(p.getRefundReason());
      e.setRefundedAt(p.getRefundedAt());
      e.setRefundedBy(p.getRefundedBy());
      e.setSettlementNotified(p.isSettlementNotified());
      e.setUpdatedAt(p.getUpdatedAt());
      e.setUpdatedBy(p.getUpdatedBy());
   }
}
