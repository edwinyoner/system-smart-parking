import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { Observable, catchError, exhaustMap, forkJoin, map, of } from 'rxjs';

import { ParkingService } from '../../core/services/parking-service/parking.service';
import { TransactionService } from '../../core/services/parking-service/transaction.service';
import { OperatorAssignmentService } from '../../core/services/parking-service/operator-assignment.service';
import { PaymentService } from '../../core/services/payment-service/payment.service';
import { CurrentUserService } from '../../core/services/auth-service/current-user.service';
import { AuthContextService } from '../../core/services/auth-service/auth-context.service';
import { Parking } from '../../core/models/parking-service/parking.model';
import { ActiveTransactionDto } from '../../core/models/parking-service/transaction.model';
import { OperatorAssignment } from '../../core/models/parking-service/operator-assignment.model';
import { Payment } from '../../core/models/payment-service/payment.model';
import { liveRefresh } from '../../core/utils/live-refresh.util';
import { localDate, locationStatus } from '../../core/utils/parking.util';
import { hhmm } from '../../core/utils/shift.util';

/** Lo que trae cada consulta. null = sin permiso o el servicio no respondió */
interface DashboardData {
  parkings: Parking[] | null;
  active: { list: ActiveTransactionDto[]; total: number } | null;
  payments: Payment[] | null;
  shifts: OperatorAssignment[] | null;
}

interface Totals {
  parkings: number;
  operational: number;
  spaces: number;
  available: number;
  occupied: number;
  unavailable: number;
  occupancy: number;
}

/**
 * Panel de inicio con datos reales: disponibilidad por parqueo (calculada desde los
 * espacios), vehículos dentro, lo cobrado hoy y los turnos del usuario.
 * Se actualiza solo cada 10 s, sin spinner. Cada bloque aparece si el rol activo tiene
 * el permiso que exige su endpoint.
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css'],
})
export class DashboardComponent implements OnInit {

  readonly #destroyRef = inject(DestroyRef);
  readonly #router = inject(Router);
  readonly #parkingService = inject(ParkingService);
  readonly #transactionService = inject(TransactionService);
  readonly #assignmentService = inject(OperatorAssignmentService);
  readonly #paymentService = inject(PaymentService);
  readonly #authContext = inject(AuthContextService);
  readonly #currentUser = toSignal(inject(CurrentUserService).currentUser$, { initialValue: null });

  readonly loading = signal(true);
  readonly refreshing = signal(false);
  readonly lastUpdate = signal<Date | null>(null);
  readonly data = signal<DashboardData>({ parkings: null, active: null, payments: null, shifts: null });

  readonly status = locationStatus;
  readonly hhmm = hhmm;
  /** "jueves, 26 de noviembre de 2026" (la app no registra el locale es de Angular) */
  readonly todayLabel = new Intl.DateTimeFormat('es-PE',
    { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }).format(new Date());

  // ========================= DATOS DERIVADOS =========================

  readonly firstName = computed(() => this.#currentUser()?.firstName ?? '');

  readonly greeting = computed(() => {
    const h = new Date().getHours();
    return h < 12 ? 'Buenos días' : h < 19 ? 'Buenas tardes' : 'Buenas noches';
  });

  readonly activeRole = computed(() => {
    this.#currentUser();
    return this.#authContext.getActiveRole() || '';
  });

  /** Suma de la capacidad de todos los parqueos (el backend la calcula desde cada espacio) */
  readonly totals = computed<Totals | null>(() => {
    const parkings = this.data().parkings;
    if (!parkings) return null;
    const t = parkings.reduce((acc, p) => ({
      parkings: acc.parkings + 1,
      operational: acc.operational + (p.operational ? 1 : 0),
      spaces: acc.spaces + (p.totalSpaces ?? 0),
      available: acc.available + (p.availableSpaces ?? 0),
      occupied: acc.occupied + (p.occupiedSpaces ?? 0),
      unavailable: acc.unavailable + (p.unavailableSpaces ?? 0),
      occupancy: 0,
    }), { parkings: 0, operational: 0, spaces: 0, available: 0, occupied: 0, unavailable: 0, occupancy: 0 });
    t.occupancy = t.spaces ? Math.round((t.occupied / t.spaces) * 100) : 0;
    return t;
  });

  /** Parqueos ordenados: primero los que atienden, luego por ocupación */
  readonly parkingRows = computed(() => [...(this.data().parkings ?? [])]
    .sort((a, b) => Number(b.operational) - Number(a.operational)
      || (b.occupancyPercentage ?? 0) - (a.occupancyPercentage ?? 0)));

  /** Dona de estados de los espacios (CSS conic-gradient: sin librería de gráficos) */
  readonly donut = computed(() => {
    const t = this.totals();
    if (!t || t.spaces === 0) return null;
    const pct = (n: number) => (n / t.spaces) * 100;
    const a = pct(t.available);
    const o = a + pct(t.occupied);
    return `conic-gradient(var(--dash-free) 0 ${a}%, var(--dash-busy) ${a}% ${o}%, var(--dash-off) ${o}% 100%)`;
  });

  readonly paidToday = computed(() => {
    const list = this.data().payments;
    if (!list) return null;
    const amount = list.reduce((sum, p) => sum + (p.amount ?? 0) - (p.refundedAmount ?? 0), 0);
    return { amount, count: list.length };
  });

  readonly recentPayments = computed(() => (this.data().payments ?? []).slice(0, 6));

  readonly onDuty = computed(() => (this.data().shifts ?? []).filter(a => a.onDutyNow));
  readonly upcomingShifts = computed(() => (this.data().shifts ?? []).filter(a => !a.onDutyNow).slice(0, 3));

  // ========================= CICLO DE VIDA =========================

  ngOnInit(): void {
    this.load(false);
    liveRefresh(10000).pipe(
      exhaustMap(() => this.request(true)),
      takeUntilDestroyed(this.#destroyRef),
    ).subscribe(d => this.apply(d));
  }

  refresh(): void {
    this.load(true);
  }

  private load(manual: boolean): void {
    manual ? this.refreshing.set(true) : this.loading.set(true);
    this.request(true).subscribe(d => this.apply(d));
  }

  private apply(d: DashboardData): void {
    this.data.set(d);
    this.loading.set(false);
    this.refreshing.set(false);
    this.lastUpdate.set(new Date());
  }

  /** Cada consulta falla por separado: un servicio caído no deja el panel en blanco */
  private request(silent: boolean): Observable<DashboardData> {
    const safe = <T>(allowed: boolean, source: () => Observable<T>): Observable<T | null> =>
      allowed ? source().pipe(catchError(() => of(null))) : of(null);
    const day = localDate();
    return forkJoin({
      parkings: safe(this.can('parkings.read'),
        () => this.#parkingService.getAll(0, 100, '', '', false, silent).pipe(map(r => r.content))),
      active: safe(this.can('transactions.view'),
        () => this.#transactionService.getActiveTransactions(0, 8, silent)
          .pipe(map(r => ({ list: r.content, total: r.totalElements })))),
      payments: safe(this.can('payments.view'),
        () => this.#paymentService.getAll(0, 100, { status: 'COMPLETED', from: day, to: day }, silent)
          .pipe(map(r => r.content))),
      shifts: safe(true, () => this.#assignmentService.listMine(undefined, silent)
        .pipe(map(list => list.filter(a => a.status !== 'ENDED')))),
    });
  }

  // ========================= VISTA =========================

  can(permission: string): boolean {
    return this.#authContext.hasPermission(permission);
  }

  open(route: (string | number)[]): void {
    this.#router.navigate(route);
  }

  /** Porcentaje de cada tramo de la barra de un parqueo */
  share(p: Parking, value?: number): number {
    return p.totalSpaces ? ((value ?? 0) / p.totalSpaces) * 100 : 0;
  }

  occupancyTone(pct?: number): string {
    if ((pct ?? 0) >= 90) return 'tone-high';
    if ((pct ?? 0) >= 70) return 'tone-mid';
    return 'tone-low';
  }

  conceptLabel(p: Payment): string {
    return p.concept === 'FINE' ? 'Multa' : 'Estadía';
  }
}
