package com.winnersystems.smartparking.parking.application.port.output;

import com.winnersystems.smartparking.parking.domain.model.ChargeConcept;
import com.winnersystems.smartparking.parking.domain.model.ChargeSettlement;

import java.util.List;
import java.util.Optional;

/** Lo que payment-service confirmó de cada pago (una fila por pago) */
public interface ChargeSettlementPersistencePort {

   ChargeSettlement save(ChargeSettlement settlement);

   Optional<ChargeSettlement> findByPaymentId(Long paymentId);

   /** Pagos de una estadía o una multa */
   List<ChargeSettlement> findBySource(ChargeConcept concept, Long sourceId);
}
