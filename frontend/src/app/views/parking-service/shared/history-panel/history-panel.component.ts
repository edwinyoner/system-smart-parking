// src/app/views/parking-service/shared/history-panel/history-panel.component.ts
import { Component, DestroyRef, Input, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Observable, Subject, catchError, exhaustMap, merge, switchMap } from 'rxjs';
import { CardBodyComponent, CardComponent, CardHeaderComponent, SpinnerComponent } from '@coreui/angular';

import { StayHistory } from '../../../../core/models/parking-service/customer.model';
import { Infraction } from '../../../../core/models/parking-service/infraction.model';
import { ParkingPagedResponse } from '../../../../core/models/pagination.model';
import { liveRefresh } from '../../../../core/utils/live-refresh.util';
import { stayPaymentStatusColor, stayPaymentStatusLabel } from '../../../../core/utils/payment.util';
import { infractionStatusInfo, infractionTypeLabel } from '../../../../core/utils/infraction.util';

type Tab = 'stays' | 'infractions';

const STAY_STATUS: Record<string, { label: string; badge: string }> = {
  ACTIVE:    { label: 'En curso',   badge: 'bg-primary' },
  COMPLETED: { label: 'Finalizada', badge: 'bg-success' },
  CANCELLED: { label: 'Cancelada',  badge: 'bg-secondary' }
};

/**
 * Historial de un cliente o de un vehículo: pestañas Estadías e Infracciones, con su propia
 * paginación y refresco en vivo cada 10 s (silencioso) de la página abierta.
 * Quien lo usa entrega cómo cargar cada página.
 */
@Component({
  selector: 'app-history-panel',
  standalone: true,
  imports: [CommonModule, RouterLink, CardComponent, CardHeaderComponent, CardBodyComponent, SpinnerComponent],
  templateUrl: './history-panel.component.html',
  styleUrls: ['./history-panel.component.css']
})
export class HistoryPanelComponent implements OnInit {

  @Input({ required: true }) loadStays!: (page: number, silent: boolean) => Observable<ParkingPagedResponse<StayHistory>>;
  @Input({ required: true }) loadInfractions!: (page: number, silent: boolean) => Observable<ParkingPagedResponse<Infraction>>;
  /** Mostrar la placa en cada estadía (en el historial de un cliente, que usa varios vehículos) */
  @Input() showPlate = false;
  @Input() canSeeInfractions = true;

  private readonly destroyRef = inject(DestroyRef);

  readonly paymentLabel = stayPaymentStatusLabel;
  readonly paymentColor = stayPaymentStatusColor;
  readonly infractionStatus = infractionStatusInfo;
  readonly infractionType = infractionTypeLabel;

  tab: Tab = 'stays';
  page = 0;
  stays: ParkingPagedResponse<StayHistory> | null = null;
  infractions: ParkingPagedResponse<Infraction> | null = null;
  loading = true;
  error = false;

  private readonly reload$ = new Subject<boolean>();

  ngOnInit(): void {
    merge(
      this.reload$.pipe(switchMap(silent => this.fetch(silent))),
      liveRefresh(10000).pipe(exhaustMap(() => this.fetch(true)))
    ).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(result => {
      if (this.tab === 'stays') this.stays = result as ParkingPagedResponse<StayHistory>;
      else this.infractions = result as ParkingPagedResponse<Infraction>;
      this.loading = false;
    });
    this.reload$.next(false);
  }

  private fetch(silent: boolean): Observable<ParkingPagedResponse<StayHistory> | ParkingPagedResponse<Infraction>> {
    const request$ = this.tab === 'stays' ? this.loadStays(this.page, silent) : this.loadInfractions(this.page, silent);
    return (request$ as Observable<ParkingPagedResponse<StayHistory> | ParkingPagedResponse<Infraction>>).pipe(
      catchError(() => {
        this.loading = false;
        if (!silent) this.error = true;
        return EMPTY;
      })
    );
  }

  select(tab: Tab): void {
    if (this.tab === tab) return;
    this.tab = tab;
    this.page = 0;
    this.loading = true;
    this.error = false;
    this.reload$.next(false);
  }

  get current(): ParkingPagedResponse<unknown> | null {
    return this.tab === 'stays' ? this.stays : this.infractions;
  }

  goTo(page: number): void {
    const c = this.current;
    if (!c || page < 0 || page >= c.totalPages) return;
    this.page = page;
    this.reload$.next(false);
  }

  stayStatus(status: string): { label: string; badge: string } {
    return STAY_STATUS[status] ?? { label: status, badge: 'bg-secondary' };
  }

  duration(minutes: number | null): string {
    if (minutes == null) return '-';
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return h > 0 ? `${h} h ${m} min` : `${m} min`;
  }
}
