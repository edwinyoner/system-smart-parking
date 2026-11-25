package com.winnersystems.smartparking.parking.application.port.input.rate;

import com.winnersystems.smartparking.parking.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.parking.application.dto.query.RateDto;

import java.util.List;

public interface ListRatesUseCase {
   /** deleted = true lista la papelera (tarifas eliminadas) */
   PagedResponse<RateDto> listAllRates(int page, int size, String search, Boolean status, boolean deleted);
   List<RateDto> listAllActiveRates();
}