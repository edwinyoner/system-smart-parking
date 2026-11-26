package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.settlement;

import com.winnersystems.smartparking.parking.application.port.output.ChargeSettlementPersistencePort;
import com.winnersystems.smartparking.parking.domain.model.ChargeConcept;
import com.winnersystems.smartparking.parking.domain.model.ChargeSettlement;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.settlement.mapper.ChargeSettlementPersistenceMapper;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.settlement.repository.ChargeSettlementRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ChargeSettlementPersistenceAdapter implements ChargeSettlementPersistencePort {

   private final ChargeSettlementRepository repository;
   private final ChargeSettlementPersistenceMapper mapper;

   public ChargeSettlementPersistenceAdapter(ChargeSettlementRepository repository, ChargeSettlementPersistenceMapper mapper) {
      this.repository = repository;
      this.mapper = mapper;
   }

   @Override
   public ChargeSettlement save(ChargeSettlement settlement) {
      return mapper.toDomain(repository.save(mapper.toEntity(settlement)));
   }

   @Override
   public Optional<ChargeSettlement> findByPaymentId(Long paymentId) {
      return repository.findByPaymentId(paymentId).map(mapper::toDomain);
   }

   @Override
   public List<ChargeSettlement> findBySource(ChargeConcept concept, Long sourceId) {
      return repository.findByConceptAndSourceIdOrderByIdAsc(concept, sourceId).stream().map(mapper::toDomain).toList();
   }
}
