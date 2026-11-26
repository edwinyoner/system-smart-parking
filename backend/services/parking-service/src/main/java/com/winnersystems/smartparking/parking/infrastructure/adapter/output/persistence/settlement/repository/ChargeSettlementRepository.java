package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.settlement.repository;

import com.winnersystems.smartparking.parking.domain.model.ChargeConcept;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.settlement.entity.ChargeSettlementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChargeSettlementRepository extends JpaRepository<ChargeSettlementEntity, Long> {

   Optional<ChargeSettlementEntity> findByPaymentId(Long paymentId);

   List<ChargeSettlementEntity> findByConceptAndSourceIdOrderByIdAsc(ChargeConcept concept, Long sourceId);
}
