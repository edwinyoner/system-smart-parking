package com.winnersystems.smartparking.parking.application.port.input.rate;

import com.winnersystems.smartparking.parking.application.dto.query.RateDto;

/**
 * Restaura una tarifa eliminada por error (papelera).
 */
public interface RestoreRateUseCase {
   RateDto restoreRate(Long id);
}
