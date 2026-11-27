// src/app/views/parking-service/infractions/infraction-list/infraction-list.component.ts
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Subject, catchError, debounceTime, exhaustMap, forkJoin, merge, switchMap } from 'rxjs';
import {
  AlertComponent,
  BadgeComponent,
  ButtonDirective,
  CardBodyComponent,
  CardComponent,
  CardHeaderComponent,
  SpinnerComponent
} from '@coreui/angular';

import { InfractionService } from '../../../../core/services/parking-service/infraction.service';
import { ParkingService } from '../../../../core/services/parking-service/parking.service';
import { PermissionService } from '../../../../core/services/auth-service/permission.service';
import {
  Infraction,
  InfractionStatusFilter,
  InfractionSummary,
  InfractionType
} from '../../../../core/models/parking-service/infraction.model';
import { Parking } from '../../../../core/models/parking-service/parking.model';
import { ParkingPagedResponse } from '../../../../core/models/pagination.model';
import { liveRefresh } from '../../../../core/utils/live-refresh.util';
import { apiError } from '../../../../core/utils/parking.util';
import {
  INFRACTION_STATUS_FILTERS,
  INFRACTION_TYPES,
  infractionStatusInfo,
  infractionTypeIcon,
  infractionTypeLabel,
  severityInfo
} from '../../../../core/utils/infraction.util';
import { takeSuccessMessage } from '../../../../core/utils/flash-message.util';

const PAGE_SIZE = 20;

/**
 * Infracciones: resumen (pendientes, vencidas, por cobrar...), filtros y tabla, en vivo cada 10 s.
 * Desde cada fila se ve el detalle, donde se cobra la multa, se edita o se resuelve.
 */
@Component({
  selector: 'app-infraction-list',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    CardComponent,
    CardBodyComponent,
    CardHeaderComponent,
    ButtonDirective,
    SpinnerComponent,
    AlertComponent,
    BadgeComponent
  ],
  templateUrl: './infraction-list.component.html',
  styleUrls: ['./infraction-list.component.css']
})
export class InfractionListComponent implements OnInit {

  private readonly infractionService = inject(InfractionService);
  private readonly parkingService = inject(ParkingService);
  private readonly permissions = inject(PermissionService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  readonly statusFilters = INFRACTION_STATUS_FILTERS;
  readonly types = INFRACTION_TYPES;
  readonly typeLabel = infractionTypeLabel;
  readonly typeIcon = infractionTypeIcon;
  readonly severity = severityInfo;
  readonly statusInfo = infractionStatusInfo;

  readonly canCreate = this.permissions.hasPermission('infractions.create');
  readonly canCharge = this.permissions.hasPermission('payments.process');

  parkings: Parking[] = [];
  status: InfractionStatusFilter = 'OPEN';
  parkingId: number | null = null;
  type: InfractionType | null = null;
  search = '';
  from = '';
  to = '';
  page = 0;

  result: ParkingPagedResponse<Infraction> | null = null;
  summary: InfractionSummary | null = null;
  loading = true;
  errorMessage = '';
  successMessage = takeSuccessMessage() ?? '';
  lastUpdate: Date | null = null;

  private readonly reload$ = new Subject<boolean>();
  private readonly search$ = new Subject<void>();

  ngOnInit(): void {
    const qp = this.route.snapshot.queryParamMap;
    const qStatus = qp.get('status') as InfractionStatusFilter | null;
    if (qStatus && this.statusFilters.some(s => s.value === qStatus)) this.status = qStatus;
    if (qp.get('parkingId')) this.parkingId = Number(qp.get('parkingId'));
    if (qp.get('search')) this.search = qp.get('search')!.toUpperCase();

    this.parkingService.getActive()
      .pipe(catchError(() => EMPTY), takeUntilDestroyed(this.destroyRef))
      .subscribe(p => (this.parkings = p));

    this.search$.pipe(debounceTime(350), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.applyFilters());

    merge(
      this.reload$.pipe(switchMap(silent => this.fetch(silent))),
      liveRefresh(10000).pipe(exhaustMap(() => this.fetch(true)))
    ).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(({ page, summary }) => {
      this.result = page;
      this.summary = summary;
      this.loading = false;
      this.lastUpdate = new Date();
      if (page.content.length === 0 && this.page > 0) {
        this.goTo(this.page - 1);
      }
    });

    this.reload$.next(false);
  }

  private fetch(silent: boolean) {
    return forkJoin({
      page: this.infractionService.list(this.page, PAGE_SIZE, {
        status: this.status, parkingId: this.parkingId, type: this.type,
        search: this.search, from: this.from || null, to: this.to || null
      }, silent),
      summary: this.infractionService.summary(this.parkingId, true)
    }).pipe(catchError(err => {
      this.loading = false;
      if (!silent) this.errorMessage = apiError(err, 'No se pudieron cargar las infracciones');
      return EMPTY;
    }));
  }

  // ========================= FILTROS =========================

  setStatus(status: InfractionStatusFilter): void {
    this.status = status;
    this.applyFilters();
  }

  onSearchInput(): void {
    this.search = this.search.toUpperCase();
    this.search$.next();
  }

  applyFilters(): void {
    if (this.from && this.to && this.to < this.from) {
      this.errorMessage = 'La fecha final no puede ser anterior a la inicial';
      return;
    }
    this.page = 0;
    this.errorMessage = '';
    this.reload$.next(false);
  }

  clearFilters(): void {
    this.status = 'OPEN';
    this.parkingId = null;
    this.type = null;
    this.search = '';
    this.from = '';
    this.to = '';
    this.applyFilters();
  }

  get hasFilters(): boolean {
    return this.status !== 'OPEN' || !!this.parkingId || !!this.type || !!this.search.trim() || !!this.from || !!this.to;
  }

  // ========================= PAGINACIÓN =========================

  goTo(page: number): void {
    if (!this.result || page < 0 || page >= this.result.totalPages) return;
    this.page = page;
    this.reload$.next(false);
  }

  get pages(): number[] {
    const total = this.result?.totalPages ?? 0;
    const from = Math.max(0, Math.min(this.page - 2, total - 5));
    return Array.from({ length: Math.min(5, total) }, (_, i) => from + i);
  }
}
