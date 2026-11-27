package com.winnersystems.smartparking.parking.application.port.input.report;

import com.winnersystems.smartparking.parking.application.dto.query.ReportDto;

import java.time.LocalDate;

/** Reportes de operación: diario por parqueo y zona, y ocupación por hora */
public interface GetReportsUseCase {

   /** @param parkingId null = todos los parqueos */
   ReportDto.DailyReport daily(LocalDate date, Long parkingId);

   /** @param zoneId null = todo el parqueo */
   ReportDto.OccupancyReport occupancy(LocalDate date, Long parkingId, Long zoneId);
}
