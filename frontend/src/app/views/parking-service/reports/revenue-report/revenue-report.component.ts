// src/app/views/parking-service/reports/revenue-report/revenue-report.component.ts
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Subject, catchError, of, switchMap } from 'rxjs';
import {
  AlertComponent, ButtonDirective, CardBodyComponent, CardComponent, CardHeaderComponent, SpinnerComponent
} from '@coreui/angular';

import { IncomeReportService } from '../../../../core/services/payment-service/income-report.service';
import { ParkingService } from '../../../../core/services/parking-service/parking.service';
import { PermissionService } from '../../../../core/services/auth-service/permission.service';
import { IncomeReport } from '../../../../core/models/payment-service/income-report.model';
import { Parking } from '../../../../core/models/parking-service/parking.model';
import { apiError, localDate } from '../../../../core/utils/parking.util';
import { CsvCell, downloadCsv, percent, printReport } from '../../../../core/utils/report.util';
import { ChartSeries, ReportChartComponent } from '../shared/report-chart/report-chart.component';

type Preset = 'today' | 'week' | 'month' | 'lastMonth' | 'custom';

/**
 * Ingresos de un rango de fechas (de payment-service): neto por día, por tipo de pago y por
 * parqueo, separando estadías y multas. Las devoluciones se restan en el día del cobro.
 */
@Component({
  selector: 'app-revenue-report',
  standalone: true,
  imports: [CommonModule, FormsModule, CardComponent, CardHeaderComponent, CardBodyComponent, ButtonDirective,
    SpinnerComponent, AlertComponent, ReportChartComponent],
  templateUrl: './revenue-report.component.html',
  styleUrls: ['../shared/report.css']
})
export class RevenueReportComponent implements OnInit {

  private readonly incomeService = inject(IncomeReportService);
  private readonly parkingService = inject(ParkingService);
  private readonly permissions = inject(PermissionService);
  private readonly destroyRef = inject(DestroyRef);

  readonly canExport = this.permissions.hasPermission('reports.export');
  readonly percent = percent;
  readonly today = localDate();
  readonly presets: { value: Preset; label: string }[] = [
    { value: 'today', label: 'Hoy' },
    { value: 'week', label: 'Últimos 7 días' },
    { value: 'month', label: 'Este mes' },
    { value: 'lastMonth', label: 'Mes anterior' }
  ];

  preset: Preset = 'week';
  from = '';
  to = '';
  parkingId: number | null = null;
  parkings: Parking[] = [];

  report: IncomeReport | null = null;
  loading = true;
  errorMessage = '';
  generatedAt = new Date();

  chartLabels: string[] = [];
  chartSeries: ChartSeries[] = [];

  private readonly reload$ = new Subject<void>();

  ngOnInit(): void {
    this.parkingService.getActive().pipe(catchError(() => of([])), takeUntilDestroyed(this.destroyRef))
      .subscribe(p => (this.parkings = p));

    this.reload$.pipe(
      switchMap(() => this.incomeService.income(this.from, this.to, this.parkingId).pipe(catchError(err => {
        this.loading = false;
        this.errorMessage = apiError(err, 'No se pudo generar el reporte de ingresos');
        return EMPTY;
      }))),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(r => {
      this.report = r;
      this.loading = false;
      this.generatedAt = new Date();
      this.chartLabels = r.byDay.map(d => this.shortDate(d.date));
      this.chartSeries = [{ name: 'Neto cobrado', values: r.byDay.map(d => d.net) }];
    });

    this.setPreset('week');
  }

  setPreset(preset: Preset): void {
    this.preset = preset;
    const now = new Date();
    const y = now.getFullYear();
    const m = now.getMonth();
    switch (preset) {
      case 'today': this.from = this.to = this.today; break;
      case 'week': this.from = localDate(new Date(y, m, now.getDate() - 6)); this.to = this.today; break;
      case 'month': this.from = localDate(new Date(y, m, 1)); this.to = this.today; break;
      case 'lastMonth': this.from = localDate(new Date(y, m - 1, 1)); this.to = localDate(new Date(y, m, 0)); break;
      default: return;
    }
    this.apply();
  }

  onDatesChanged(): void {
    this.preset = 'custom';
    this.apply();
  }

  apply(): void {
    if (!this.from || !this.to) return;
    if (this.to < this.from) {
      this.errorMessage = 'La fecha final no puede ser anterior a la inicial';
      return;
    }
    if (this.from > this.today) {
      this.errorMessage = 'La fecha inicial no puede ser futura';
      return;
    }
    this.errorMessage = '';
    this.loading = true;
    this.reload$.next();
  }

  /** "27/11" */
  shortDate(iso: string): string {
    const [, m, d] = iso.split('-');
    return `${d}/${m}`;
  }

  money = (v: number): string => `S/ ${v.toFixed(v % 1 === 0 ? 0 : 2)}`;

  get daysWithIncome(): number {
    return this.report?.byDay.filter(d => d.payments > 0).length ?? 0;
  }

  get averagePerDay(): number {
    const r = this.report;
    return r && r.byDay.length > 0 ? r.totals.net / r.byDay.length : 0;
  }

  // ========================= EXPORTAR =========================

  exportCsv(): void {
    const r = this.report;
    if (!r) return;
    const parking = this.parkingId ? this.parkings.find(p => p.id === this.parkingId)?.name ?? '' : 'Todos';
    const f = (n: number) => n.toFixed(2);
    const rows: CsvCell[][] = [
      ['Reporte de ingresos', `${r.from} a ${r.to}`, parking],
      [],
      ['Totales'],
      ['Pagos', 'Cobrado (S/)', 'Devuelto (S/)', 'Neto (S/)', 'Estadias (S/)', 'Multas (S/)'],
      [r.totals.payments, f(r.totals.gross), f(r.totals.refunded), f(r.totals.net), f(r.totals.stays), f(r.totals.fines)],
      [],
      ['Por dia'],
      ['Fecha', 'Pagos', 'Cobrado (S/)', 'Devuelto (S/)', 'Neto (S/)'],
      ...r.byDay.map(d => [d.date, d.payments, f(d.gross), f(d.refunded), f(d.net)] as CsvCell[]),
      [],
      ['Por tipo de pago'],
      ['Tipo', 'Pagos', 'Neto (S/)'],
      ...r.byPaymentType.map(t => [t.name ?? t.code, t.payments, f(t.net)] as CsvCell[]),
      [],
      ['Por parqueo'],
      ['Parqueo', 'Pagos', 'Estadias (S/)', 'Multas (S/)', 'Neto (S/)'],
      ...r.byParking.map(p => [p.parkingName ?? 'Sin parqueo', p.payments, f(p.stays), f(p.fines), f(p.net)] as CsvCell[])
    ];
    downloadCsv(`reporte-ingresos-${r.from}-a-${r.to}.csv`, rows);
  }

  print(): void {
    printReport();
  }
}
