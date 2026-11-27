package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.report;

import com.winnersystems.smartparking.parking.application.dto.query.ReportDto;
import com.winnersystems.smartparking.parking.application.port.input.report.GetReportsUseCase;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Reportes de operación (permiso reports.view). Los ingresos están en payment-service
 * (/api/payment-service/v1/reports/income); la exportación a CSV y PDF la hace el panel.
 */
@RestController
@RequestMapping("/reports")
public class ReportRestAdapter {

   private final GetReportsUseCase reports;

   public ReportRestAdapter(GetReportsUseCase reports) {
      this.reports = reports;
   }

   /**
    * Cada parqueo con sus zonas: entradas, salidas, vehículos que estuvieron, dentro al cierre,
    * tiempo promedio, infracciones y hora pico.
    *
    * @param date      día (por defecto hoy)
    * @param parkingId null = todos los parqueos
    */
   @GetMapping("/daily")
   @PreAuthorize("hasAuthority('reports.view')")
   public ResponseEntity<ReportDto.DailyReport> daily(
         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
         @RequestParam(required = false) Long parkingId) {
      return ResponseEntity.ok(reports.daily(date, parkingId));
   }

   /** Ocupación hora por hora de un parqueo o de una de sus zonas */
   @GetMapping("/occupancy")
   @PreAuthorize("hasAuthority('reports.view')")
   public ResponseEntity<ReportDto.OccupancyReport> occupancy(
         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
         @RequestParam Long parkingId,
         @RequestParam(required = false) Long zoneId) {
      return ResponseEntity.ok(reports.occupancy(date, parkingId, zoneId));
   }
}
