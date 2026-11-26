// src/app/views/parking-service/transactions/active-transactions/active-transactions.component.ts
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Subject, catchError, debounceTime, exhaustMap, merge, switchMap } from 'rxjs';
import {
  CardComponent,
  CardBodyComponent,
  CardHeaderComponent,
  ButtonDirective,
  SpinnerComponent,
  AlertComponent,
  BadgeComponent
} from '@coreui/angular';

import { TransactionService } from '../../../../core/services/parking-service/transaction.service';
import { ParkingService } from '../../../../core/services/parking-service/parking.service';
import { ZoneService } from '../../../../core/services/parking-service/zone.service';
import { PermissionService } from '../../../../core/services/auth-service/permission.service';
import { ActiveTransactionDto } from '../../../../core/models/parking-service/transaction.model';
import { Parking } from '../../../../core/models/parking-service/parking.model';
import { Zone } from '../../../../core/models/parking-service/zone.model';
import { ParkingPagedResponse } from '../../../../core/models/pagination.model';
import { liveRefresh } from '../../../../core/utils/live-refresh.util';
import { confidencePercent } from '../../../../core/utils/plate.util';
import { apiError } from '../../../../core/utils/parking.util';
import { StayAction, StayActionModalComponent, StayRef } from '../shared/stay-action-modal.component';

const PAGE_SIZE = 20;

/**
 * Vehículos dentro: las estadías en curso, en vivo (cada 10 s, sin spinner).
 * Filtros por parqueo, zona y placa (en parte, sin guion). Desde cada fila: ver la estadía,
 * registrar la salida, cambiar de espacio o cancelar (según permisos).
 * El estado de cada espacio en cuadros está en el Mapa de ocupación.
 */
@Component({
  selector: 'app-active-transactions',
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
    BadgeComponent,
    StayActionModalComponent
  ],
  templateUrl: './active-transactions.component.html',
  styleUrls: ['./active-transactions.component.css']
})
export class ActiveTransactionsComponent implements OnInit {

  private readonly transactionService = inject(TransactionService);
  private readonly parkingService = inject(ParkingService);
  private readonly zoneService = inject(ZoneService);
  private readonly permissions = inject(PermissionService);
  private readonly destroyRef = inject(DestroyRef);

  parkings: Parking[] = [];
  zones: Zone[] = [];
  parkingId: number | null = null;
  zoneId: number | null = null;
  plate = '';
  page = 0;

  result: ParkingPagedResponse<ActiveTransactionDto> | null = null;
  loading = true;
  errorMessage = '';
  successMessage = '';
  lastUpdate: Date | null = null;

  // Modal de acciones
  actionStay: StayRef | null = null;
  action: StayAction | null = null;

  readonly canExit = this.permissions.hasPermission('transactions.exit');
  readonly canMove = this.permissions.hasPermission('transactions.entry');
  readonly canCancel = this.permissions.hasPermission('transactions.cancel');
  readonly canSeeMap = this.permissions.hasPermission('occupancy.view');
  readonly confidencePercent = confidencePercent;

  /** Un cambio de filtro o de página recarga con spinner; el refresco en vivo, en silencio */
  private readonly reload$ = new Subject<boolean>();
  private readonly plate$ = new Subject<void>();

  ngOnInit(): void {
    this.parkingService.getActive()
      .pipe(catchError(() => EMPTY), takeUntilDestroyed(this.destroyRef))
      .subscribe(p => (this.parkings = p));

    this.plate$.pipe(debounceTime(350), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.applyFilters());

    merge(
      this.reload$.pipe(switchMap(silent => this.fetch(silent))),
      liveRefresh(10000).pipe(exhaustMap(() => this.fetch(true)))
    ).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(result => {
      this.result = result;
      this.loading = false;
      this.lastUpdate = new Date();
      // Si la página quedó vacía (salieron vehículos), se vuelve a la anterior
      if (result.content.length === 0 && this.page > 0) {
        this.goTo(this.page - 1);
      }
    });

    this.reload$.next(false);
  }

  private fetch(silent: boolean) {
    return this.transactionService.getActiveTransactions(this.page, PAGE_SIZE, silent,
      { parkingId: this.parkingId, zoneId: this.zoneId, plate: this.plate })
      .pipe(catchError(err => {
        this.loading = false;
        if (!silent) this.errorMessage = apiError(err, 'No se pudieron cargar los vehículos dentro');
        return EMPTY;
      }));
  }

  // ========================= FILTROS =========================

  onParkingChange(): void {
    this.zoneId = null;
    this.zones = [];
    if (this.parkingId) {
      this.zoneService.getActive(this.parkingId)
        .pipe(catchError(() => EMPTY), takeUntilDestroyed(this.destroyRef))
        .subscribe(z => (this.zones = z));
    }
    this.applyFilters();
  }

  onPlateInput(): void {
    this.plate = this.plate.toUpperCase();
    this.plate$.next();
  }

  applyFilters(): void {
    this.page = 0;
    this.errorMessage = '';
    this.reload$.next(false);
  }

  clearFilters(): void {
    this.parkingId = null;
    this.zoneId = null;
    this.zones = [];
    this.plate = '';
    this.applyFilters();
  }

  get hasFilters(): boolean {
    return !!this.parkingId || !!this.zoneId || !!this.plate.trim();
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

  // ========================= ACCIONES =========================

  open(action: StayAction, t: ActiveTransactionDto): void {
    this.actionStay = {
      id: t.id, plateNumber: t.plateNumber, zoneId: t.zoneId, zoneName: t.zoneName, spaceCode: t.spaceCode
    };
    this.action = action;
  }

  onActionDone(message: string): void {
    this.successMessage = message;
    this.reload$.next(true);
  }

  closeAction(): void {
    this.action = null;
    this.actionStay = null;
  }
}
