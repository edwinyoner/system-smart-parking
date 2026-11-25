package com.winnersystems.smartparking.parking.application.port.input.rate;

import com.winnersystems.smartparking.parking.application.dto.query.RateSimulationDto;
import com.winnersystems.smartparking.parking.domain.model.Rate.BillingRules;

import java.math.BigDecimal;
import java.util.List;

/**
 * Simula el cobro con los datos de un formulario, sin guardar nada.
 * Usa exactamente el mismo cálculo que la salida de un vehículo (Rate.calculateCost).
 */
public interface SimulateRateUseCase {

   /** Duraciones de ejemplo cuando no se indican */
   List<Integer> DEFAULT_DURATIONS = List.of(4, 5, 30, 60, 65, 66, 90, 120, 125, 126, 180);

   RateSimulationDto simulate(BigDecimal amount, BillingRules rules, List<Integer> durations);
}
