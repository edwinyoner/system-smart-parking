// src/app/views/parking-service/reports/daily-report/daily-report.component.ts
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Observable, Subject, catchError, exhaustMap, filter, forkJoin, merge, of, switchMap } from 'rxjs';
import {
  AlertComponent, ButtonDirective, CardBodyComponent, CardComponent, CardHeaderComponent, SpinnerComponent
} from '@coreui/angular';

import { ReportService } from '../../../../core/services/parking-service/report.service';
import { IncomeReportService } from '../../../../core/services/payment-service/income-report.service';
import { ParkingService } from '../../../../core/services/parking-service/parking.service';
import { PermissionService } from '../../../../core/services/auth-service/permission.service';
import { DailyReport, ParkingDay } from '../../../../core/models/parking-service/report.model';
import { IncomeReport } from '../../../../core/models/payment-service/income-report.model';
import { Parking } from '../../../../core/models/parking-service/parking.model';
import { liveRefresh } from '../../../../core/utils/live-refresh.util';
import { apiError, localDate } from '../../../../core/utils/parking.util';
import { CsvCell, downloadCsv, hourLabel, longDate, minutesLabel, printReport } from '../../../../core/utils/report.util';
import { ChartSeries, ReportChartComponent } from '../shared/report-chart/report-chart.component';

interface Loaded {
  report: DailyReport;
  income: IncomeReport | null;
}

/**
 * Reporte diario: por cada parqueo y cada una de sus zonas, cuántos vehículos estuvieron,
 * entradas, salidas, los que quedaron dentro, tiempo promedio, infracciones, hora pico y lo
 * cobrado (de payment-service). Si el día es hoy se actualiza solo cada 30 s.
 */
@Component({
  selector: 'app-daily-report',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, CardComponent, CardHeaderComponent, CardBodyComponent,
    ButtonDirective, SpinnerComponent, AlertComponent, ReportChartComponent],
  templateUrl: './daily-report.component.html',
  styleUrls: ['../shared/report.css', './daily-report.component.css']
})
export class DailyReportComponent implements OnInit {

  private readonly reportService = inject(ReportService);
  private readonly incomeService = inject(IncomeReportService);
  private readonly parkingService = inject(ParkingService);
  private readonly permissions = inject(PermissionService);
  private readonly destroyRef = inject(DestroyRef);

  readonly canExport = this.permissions.hasPermission('reports.export');
  readonly hourLabel = hourLabel;
  readonly minutesLabel = minutesLabel;
  readonly longDate = longDate;
  readonly today = localDate();

  date = this.today;
  parkingId: number | null = null;
  parkings: Parking[] = [];

  report: DailyReport | null = null;
  income: IncomeReport | null = null;
  incomeFailed = false;
  loading = true;
  errorMessage = '';
  generatedAt = new Date();

  chartLabels: string[] = [];
  chartSeries: ChartSeries[] = [];

  private readonly reload$ = new Subject<boolean>();

  ngOnInit(): void {
    this.parkingService.getActive().pipe(catchError(() => of([])), takeUntilDestroyed(this.destroyRef))
      .subscribe(p => (this.parkings = p));

    merge(
      this.reload$.pipe(switchMap(silent => this.fetch(silent))),
      liveRefresh(30000).pipe(filter(() => this.date === this.today), exhaustMap(() => this.fetch(true)))
    ).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(({ report, income }) => {
      this.report = report;
      this.income = income;
      this.incomeFailed = income === null;
      this.loading = false;
      this.generatedAt = new Date();
      this.buildChart();
    });
    this.reload$.next(false);
  }

  private fetch(silent: boolean): Observable<Loaded> {
    return forkJoin({
      report: this.reportService.daily(this.date, this.parkingId, silent),
      // Si payment-service no responde, el reporte sale igual sin montos
      income: this.incomeService.income(this.date, this.date, this.parkingId, true)
        .pipe(catchError(() => of(null)))
    }).pipe(catchError(err => {
      this.loading = false;
      if (!silent) this.errorMessage = apiError(err, 'No se pudo generar el reporte diario');
      return EMPTY;
    }));
  }

  apply(): void {
    if (!this.date || this.date > this.today) {
      this.errorMessage = 'Elige una fecha de hoy o anterior';
      return;
    }
    this.errorMessage = '';
    this.loading = true;
    this.reload$.next(false);
  }

  shiftDay(days: number): void {
    const [y, m, d] = this.date.split('-').map(Number);
    const next = localDate(new Date(y, m - 1, d + days));
    if (next > this.today) return;
    this.date = next;
    this.apply();
  }

  get isToday(): boolean {
    return this.report?.date === this.today;
  }

  /** Neto cobrado de un parqueo ese día (null si no se pudo consultar payment-service) */
  collected(parkingId: number): number | null {
    if (!this.income) return null;
    return this.income.byParking.find(p => p.parkingId === parkingId)?.net ?? 0;
  }

  private buildChart(): void {
    const rows = (this.report?.parkings ?? []).flatMap(p =>
      p.zones.map(z => ({ label: `${p.parkingCode} · ${z.zoneName}`, value: z.summary.vehicles })));
    this.chartLabels = rows.map(r => r.label);
    this.chartSeries = [{ name: 'Vehículos', values: rows.map(r => r.value) }];
  }

  get chartHeight(): number {
    return Math.max(160, this.chartLabels.length * 34 + 40);
  }

  trackParking(_: number, p: ParkingDay): number {
    return p.parkingId;
  }

  // ========================= EXPORTAR =========================

  exportCsv(): void {
    const r = this.report;
    if (!r) return;
    const rows: CsvCell[][] = [
      ['Reporte diario', r.date],
      [],
      ['Parqueo', 'Zona', 'Espacios', 'Vehiculos', 'Estadias', 'Entradas', 'Salidas', 'Dentro al cierre',
        'Tiempo promedio (min)', 'Infracciones', 'Canceladas', 'Hora pico', 'Maximo a la vez', 'Cobrado neto (S/)']
    ];
    for (const p of r.parkings) {
      for (const z of p.zones) {
        rows.push([p.parkingName, z.zoneName, z.totalSpaces, ...this.summaryCells(z.summary), hourLabel(z.peakHour),
          z.peakOccupied, null]);
      }
      const net = this.collected(p.parkingId);
      rows.push([p.parkingName, 'TOTAL', p.totalSpaces, ...this.summaryCells(p.summary), hourLabel(p.peakHour),
        p.peakOccupied, net == null ? null : net.toFixed(2)]);
    }
    rows.push(['TODOS', 'TOTAL', null, ...this.summaryCells(r.totals), null, null,
      this.income ? this.income.totals.net.toFixed(2) : null]);
    downloadCsv(`reporte-diario-${r.date}.csv`, rows);
  }

  private summaryCells(s: DailyReport['totals']): CsvCell[] {
    return [s.vehicles, s.stays, s.entries, s.exits, s.insideAtClose, s.averageStayMinutes, s.infractions, s.cancelled];
  }

  print(): void {
    printReport();
  }
}
