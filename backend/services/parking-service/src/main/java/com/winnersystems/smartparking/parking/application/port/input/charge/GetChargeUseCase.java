package com.winnersystems.smartparking.parking.application.port.input.charge;

import com.winnersystems.smartparking.parking.application.dto.query.ChargeDto;
import com.winnersystems.smartparking.parking.domain.model.ChargeConcept;

/** Cuánto se debe ahora por una estadía o una multa */
public interface GetChargeUseCase {
   ChargeDto getCharge(ChargeConcept concept, Long sourceId);
}
