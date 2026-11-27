// src/app/views/parking-service/infractions/infraction-detail/infraction-detail.component.ts
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, exhaustMap, of } from 'rxjs';
import {
  AlertComponent,
  ButtonDirective,
  CardBodyComponent,
  CardComponent,
  CardHeaderComponent,
  SpinnerComponent
} from '@coreui/angular';

import { InfractionService } from '../../../../core/services/parking-service/infraction.service';
import { PaymentService } from '../../../../core/services/payment-service/payment.service';
import { PermissionService } from '../../../../core/services/auth-service/permission.service';
import { Infraction } from '../../../../core/models/parking-service/infraction.model';
import { Payment } from '../../../../core/models/payment-service/payment.model';
import { liveRefresh } from '../../../../core/utils/live-refresh.util';
import { apiError, localDate } from '../../../../core/utils/parking.util';
import { takeSuccessMessage } from '../../../../core/utils/flash-message.util';
import { paymentStatusBadge, paymentStatusLabel } from '../../../../core/utils/payment.util';
import {
  DISMISS_REASONS,
  INFRACTION_FINE_MAX,
  infractionStatusInfo,
  infractionTypeIcon,
  infractionTypeLabel,
  severityInfo
} from '../../../../core/utils/infraction.util';
import { ChargePaymentComponent } from '../../../payment-service/payments/charge-payment/charge-payment.component';

type Panel = 'charge' | 'fine' | 'dismiss' | 'warning' | null;

/**
 * Detalle de una infracción, en vivo cada 5 s (así se ve al instante el pago por QR que
 * confirma payment-service). Desde aquí: cobrar la multa (QR o efectivo), asignarla o
 * cambiarla, anular con motivo o cerrar con amonestación, según permisos.
 */
@Component({
  selector: 'app-infraction-detail',
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
    ChargePaymentComponent
  ],
  templateUrl: './infraction-detail.component.html',
  styleUrls: ['./infraction-detail.component.css']
})
export class InfractionDetailComponent implements OnInit {

  private readonly route = inject(ActivatedRoute);
  private readonly infractionService = inject(InfractionService);
  private readonly paymentService = inject(PaymentService);
  private readonly permissions = inject(PermissionService);
  private readonly destroyRef = inject(DestroyRef);

  readonly typeLabel = infractionTypeLabel;
  readonly typeIcon = infractionTypeIcon;
  readonly severity = severityInfo;
  readonly statusInfo = infractionStatusInfo;
  readonly paymentStatusLabel = paymentStatusLabel;
  readonly paymentStatusBadge = paymentStatusBadge;
  readonly dismissReasons = DISMISS_REASONS;
  readonly fineMax = INFRACTION_FINE_MAX;
  readonly minDueDate = localDate();

  readonly canUpdate = this.permissions.hasPermission('infractions.update');
  readonly canResolve = this.permissions.hasPermission('infractions.resolve');
  readonly canCharge = this.permissions.hasPermission('payments.process');
  readonly canSeePayments = this.permissions.hasPermission('payments.view');
  readonly canSeeStay = this.permissions.hasPermission('transactions.view');

  id = 0;
  infraction: Infraction | null = null;
  payments: Payment[] = [];
  loading = true;
  errorMessage = '';
  successMessage = takeSuccessMessage() ?? '';

  panel: Panel = null;
  saving = false;
  panelError = '';
  fineAmount: number | null = null;
  fineDueDate = '';
  reason = '';

  /** Para recargar los pagos solo cuando cambia lo pagado */
  private lastPaid: number | null = null;

  ngOnInit(): void {
    this.id = Number(this.route.snapshot.paramMap.get('id'));
    const wantsCharge = this.route.snapshot.queryParamMap.get('charge') === '1';

    this.infractionService.getById(this.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: i => {
        this.apply(i);
        this.loading = false;
        if (wantsCharge && i.payable && this.canCharge) this.panel = 'charge';
      },
      error: err => {
        this.errorMessage = apiError(err, 'No se pudo cargar la infracción');
        this.loading = false;
      }
    });

    liveRefresh(5000).pipe(
      exhaustMap(() => this.infraction ? this.infractionService.getById(this.id, true).pipe(catchError(() => EMPTY)) : EMPTY),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(i => this.apply(i));
  }

  private apply(i: Infraction): void {
    const paidChanged = this.lastPaid !== i.paidAmount;
    this.infraction = i;
    this.lastPaid = i.paidAmount;
    // Si ya no se puede cobrar (se pagó o se anuló), se cierra el panel de cobro
    if (this.panel === 'charge' && !i.payable) this.panel = null;
    if (paidChanged) this.loadPayments();
  }

  private loadPayments(): void {
    if (!this.canSeePayments) return;
    this.paymentService.getBySource('FINE', this.id, true)
      .pipe(catchError(() => of([] as Payment[])), takeUntilDestroyed(this.destroyRef))
      .subscribe(p => (this.payments = p));
  }

  // ========================= PANELES =========================

  open(panel: Panel): void {
    this.panel = this.panel === panel ? null : panel;
    this.panelError = '';
    this.reason = '';
    if (panel === 'fine' && this.infraction) {
      this.fineAmount = this.infraction.fineAmount;
      this.fineDueDate = this.infraction.fineDueDate ?? this.defaultDueDate();
    }
  }

  private defaultDueDate(): string {
    const d = new Date();
    d.setDate(d.getDate() + 7);
    return localDate(d);
  }

  onPaid(payment: Payment): void {
    this.successMessage = `Pago de S/ ${payment.amount.toFixed(2)} registrado (${payment.paymentTypeName})`;
    this.panel = null;
    this.refresh();
  }

  get fineAmountValid(): boolean {
    const v = this.fineAmount;
    return v != null && v > 0 && v <= INFRACTION_FINE_MAX && /^\d+(\.\d{1,2})?$/.test(String(v));
  }

  saveFine(remove = false): void {
    if (!remove && !this.fineAmountValid) {
      this.panelError = `El monto debe estar entre S/ 0.01 y S/ ${INFRACTION_FINE_MAX}, con hasta 2 decimales`;
      return;
    }
    this.run(this.infractionService.changeFine(this.id, remove ? null : this.fineAmount, remove ? null : this.fineDueDate || null),
      remove ? 'Multa quitada' : 'Multa guardada');
  }

  dismiss(): void {
    if (this.reason.trim().length < 5) {
      this.panelError = 'Indica el motivo de la anulación (al menos 5 caracteres)';
      return;
    }
    this.run(this.infractionService.dismiss(this.id, this.reason.trim()), 'Infracción anulada');
  }

  closeWithWarning(): void {
    this.run(this.infractionService.closeWithWarning(this.id, this.reason.trim() || null),
      'Infracción cerrada con amonestación');
  }

  private run(request$: ReturnType<InfractionService['getById']>, message: string): void {
    this.saving = true;
    this.panelError = '';
    request$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: i => {
        this.saving = false;
        this.panel = null;
        this.successMessage = message;
        this.apply(i);
      },
      error: err => {
        this.saving = false;
        this.panelError = apiError(err, 'No se pudo completar la acción');
        this.refresh();
      }
    });
  }

  private refresh(): void {
    this.infractionService.getById(this.id, true)
      .pipe(catchError(() => EMPTY), takeUntilDestroyed(this.destroyRef))
      .subscribe(i => this.apply(i));
  }

  isImage(url: string): boolean {
    return /\.(jpe?g|png|webp|gif)(\?|$)/i.test(url);
  }
}
