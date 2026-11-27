// src/app/views/parking-service/reports/occupancy-report/occupancy-report.component.ts
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Observable, Subject, catchError, exhaustMap, filter, merge, of, switchMap } from 'rxjs';
import {
  AlertComponent, ButtonDirective, CardBodyComponent, CardComponent, CardHeaderComponent, SpinnerComponent
} from '@coreui/angular';

import { ReportService } from '../../../../core/services/parking-service/report.service';
import { ParkingService } from '../../../../core/services/parking-service/parking.service';
import { PermissionService } from '../../../../core/services/auth-service/permission.service';
import { OccupancyReport } from '../../../../core/models/parking-service/report.model';
import { Parking } from '../../../../core/models/parking-service/parking.model';
import { liveRefresh } from '../../../../core/utils/live-refresh.util';
import { apiError, localDate } from '../../../../core/utils/parking.util';
import { CsvCell, downloadCsv, hourLabel, hourRange, longDate, percent, printReport } from '../../../../core/utils/report.util';
import { ChartSeries, ReportChartComponent } from '../shared/report-chart/report-chart.component';

/** A partir de este porcentaje la ocupación se marca como alta */
const HIGH_OCCUPANCY = 85;

/**
 * Ocupación hora por hora de un parqueo o de una zona, calculada desde las estadías:
 * los que estaban dentro al empezar cada hora, el máximo a la vez, entradas y salidas,
 * comparados con la capacidad. Si el día es hoy se actualiza solo cada 30 s.
 */
@Component({
  selector: 'app-occupancy-report',
  standalone: true,
  imports: [CommonModule, FormsModule, CardComponent, CardHeaderComponent, CardBodyComponent, ButtonDirective,
    SpinnerComponent, AlertComponent, ReportChartComponent],
  templateUrl: './occupancy-report.component.html',
  styleUrls: ['../shared/report.css']
})
export class OccupancyReportComponent implements OnInit {

  private readonly reportService = inject(ReportService);
  private readonly parkingService = inject(ParkingService);
  private readonly permissions = inject(PermissionService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  readonly canExport = this.permissions.hasPermission('reports.export');
  readonly hourLabel = hourLabel;
  readonly hourRange = hourRange;
  readonly percent = percent;
  readonly longDate = longDate;
  readonly high = HIGH_OCCUPANCY;
  readonly today = localDate();

  date = this.today;
  parkingId: number | null = null;
  zoneId: number | null = null;
  parkings: Parking[] = [];

  report: OccupancyReport | null = null;
  loading = true;
  errorMessage = '';
  generatedAt = new Date();

  chartLabels: string[] = [];
  chartSeries: ChartSeries[] = [];

  private readonly reload$ = new Subject<boolean>();

  ngOnInit(): void {
    const qp = this.route.snapshot.queryParamMap;
    if (qp.get('date')) this.date = qp.get('date')!;

    merge(
      this.reload$.pipe(switchMap(silent => this.fetch(silent))),
      liveRefresh(30000).pipe(filter(() => this.date === this.today && !!this.report), exhaustMap(() => this.fetch(true)))
    ).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(r => {
      this.report = r;
      this.loading = false;
      this.generatedAt = new Date();
      this.chartLabels = r.hours.map(h => hourLabel(h.hour));
      this.chartSeries = [
        { name: 'Máximo dentro a la vez', values: r.hours.map(h => h.maxOccupied) },
        { name: 'Entradas', values: r.hours.map(h => h.entries) },
        { name: `Capacidad (${r.capacity})`, values: r.hours.map(() => r.capacity), reference: true }
      ];
    });

    // El reporte es de un parqueo: se elige el de la URL o el primero
    this.parkingService.getActive().pipe(catchError(() => of([] as Parking[])), takeUntilDestroyed(this.destroyRef))
      .subscribe(p => {
        this.parkings = p;
        const wanted = Number(qp.get('parkingId'));
        this.parkingId = p.some(x => x.id === wanted) ? wanted : p[0]?.id ?? null;
        if (this.parkingId) {
          this.reload$.next(false);
        } else {
          this.loading = false;
        }
      });
  }

  private fetch(silent: boolean): Observable<OccupancyReport> {
    if (!this.parkingId) return EMPTY;
    return this.reportService.occupancy(this.date, this.parkingId, this.zoneId, silent).pipe(catchError(err => {
      this.loading = false;
      if (!silent) this.errorMessage = apiError(err, 'No se pudo generar el reporte de ocupación');
      return EMPTY;
    }));
  }

  onParkingChanged(): void {
    this.zoneId = null;
    this.apply();
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

  /** Porcentaje del pico sobre la capacidad */
  get peakPercent(): number {
    return this.report ? percent(this.report.peakOccupied, this.report.capacity) : 0;
  }

  // ========================= EXPORTAR =========================

  exportCsv(): void {
    const r = this.report;
    if (!r) return;
    const rows: CsvCell[][] = [
      ['Reporte de ocupacion', r.date, r.parkingName, r.zoneName ?? 'Todo el parqueo', `Capacidad ${r.capacity}`],
      [],
      ['Hora', 'Dentro al inicio', 'Maximo a la vez', 'Ocupacion maxima (%)', 'Entradas', 'Salidas', 'Vehiculos'],
      ...r.hours.filter(h => !h.future).map(h => [hourRange(h.hour), h.occupiedAtStart, h.maxOccupied,
        percent(h.maxOccupied, r.capacity), h.entries, h.exits, h.vehicles] as CsvCell[]),
      [],
      ['Total del dia', null, r.peakOccupied, percent(r.peakOccupied, r.capacity), r.entries, r.exits, r.vehicles]
    ];
    const zone = r.zoneId ? `-zona-${r.zoneId}` : '';
    downloadCsv(`reporte-ocupacion-${r.date}-parqueo-${r.parkingId}${zone}.csv`, rows);
  }

  print(): void {
    printReport();
  }
}
