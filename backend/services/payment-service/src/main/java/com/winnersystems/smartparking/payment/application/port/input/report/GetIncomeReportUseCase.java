package com.winnersystems.smartparking.payment.application.port.input.report;

import com.winnersystems.smartparking.payment.application.dto.query.IncomeReportDto;

import java.time.LocalDate;

public interface GetIncomeReportUseCase {

   /**
    * @param from      primer día (por defecto hoy)
    * @param to        último día, incluido (por defecto igual a from)
    * @param parkingId null = todos los parqueos
    */
   IncomeReportDto.IncomeReport income(LocalDate from, LocalDate to, Long parkingId);
}
