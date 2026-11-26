package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.settlement.mapper;

import com.winnersystems.smartparking.parking.domain.model.ChargeSettlement;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.settlement.entity.ChargeSettlementEntity;
import org.springframework.stereotype.Component;

@Component
public class ChargeSettlementPersistenceMapper {

   public ChargeSettlement toDomain(ChargeSettlementEntity e) {
      ChargeSettlement s = new ChargeSettlement();
      s.setId(e.getId());
      s.setConcept(e.getConcept());
      s.setSourceId(e.getSourceId());
      s.setPaymentId(e.getPaymentId());
      s.setPaymentStatus(e.getPaymentStatus());
      s.setAmount(e.getAmount());
      s.setRefundedAmount(e.getRefundedAmount());
      s.setPaidAt(e.getPaidAt());
      s.setCreatedAt(e.getCreatedAt());
      s.setUpdatedAt(e.getUpdatedAt());
      return s;
   }

   public ChargeSettlementEntity toEntity(ChargeSettlement s) {
      ChargeSettlementEntity e = new ChargeSettlementEntity();
      e.setId(s.getId());
      e.setConcept(s.getConcept());
      e.setSourceId(s.getSourceId());
      e.setPaymentId(s.getPaymentId());
      e.setPaymentStatus(s.getPaymentStatus());
      e.setAmount(s.getAmount());
      e.setRefundedAmount(s.getRefundedAmount());
      e.setPaidAt(s.getPaidAt());
      e.setCreatedAt(s.getCreatedAt());
      e.setUpdatedAt(s.getUpdatedAt());
      return e;
   }
}
