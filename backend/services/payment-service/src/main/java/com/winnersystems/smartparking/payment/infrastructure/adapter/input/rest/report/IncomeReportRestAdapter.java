package com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.report;

import com.winnersystems.smartparking.payment.application.dto.query.IncomeReportDto;
import com.winnersystems.smartparking.payment.application.port.input.report.GetIncomeReportUseCase;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** Reporte de ingresos (permiso reports.view) */
@RestController
@RequestMapping("/reports")
public class IncomeReportRestAdapter {

   private final GetIncomeReportUseCase reports;

   public IncomeReportRestAdapter(GetIncomeReportUseCase reports) {
      this.reports = reports;
   }

   /** Por día, tipo de pago y parqueo. from y to incluidos; máximo un año */
   @GetMapping("/income")
   @PreAuthorize("hasAuthority('reports.view')")
   public ResponseEntity<IncomeReportDto.IncomeReport> income(
         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
         @RequestParam(required = false) Long parkingId) {
      return ResponseEntity.ok(reports.income(from, to, parkingId));
   }
}
