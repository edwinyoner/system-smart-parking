import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, exhaustMap, filter } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { PaymentService } from '../../../../core/services/payment-service/payment.service';
import { AuthContextService } from '../../../../core/services/auth-service/auth-context.service';
import { Payment } from '../../../../core/models/payment-service/payment.model';
import { AlertMessageComponent } from '../../../../shared/components/alert-message/alert-message.component';
import { liveRefresh } from '../../../../core/utils/live-refresh.util';
import { channelLabel, confirmationLabel } from '../../../../core/utils/payment-type.util';
import {
  paymentConceptLabel, paymentSourceLink, paymentStatusBadge, paymentStatusIcon, paymentStatusLabel,
} from '../../../../core/utils/payment.util';

/**
 * Detalle de un pago. Acciones:
 * - Devolver (payments.refund): solo manual, total o parcial, hasta lo que queda por devolver.
 *   Si se devuelve el pago de una estadía activa o de una multa, vuelve a quedar como deuda.
 * - Anular QR (payments.cancel): solo un QR pendiente.
 */
@Component({
  selector: 'app-payment-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertMessageComponent],
  templateUrl: './payment-detail.component.html',
  styleUrls: ['./payment-detail.component.css']
})
export class PaymentDetailComponent implements OnInit {

  payment: Payment | null = null;
  isLoading = true;
  errorMessage: string | null = null;
  successMessage: string | null = null;

  // Devolución
  showRefund = false;
  refundAmount: number | null = null;
  refundReason = '';
  // Anulación
  showCancel = false;
  cancelReason = '';
  saving = false;

  private paymentId = 0;
  private destroyRef = inject(DestroyRef);

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private paymentService: PaymentService,
    private authContext: AuthContextService,
  ) {}

  ngOnInit(): void {
    this.paymentId = Number(this.route.snapshot.paramMap.get('id'));
    this.paymentService.getById(this.paymentId).subscribe({
      next: p => {
        this.payment = p;
        this.isLoading = false;
      },
      error: error => {
        this.isLoading = false;
        this.errorMessage = error.status === 404 ? 'El pago no existe' : (error.error?.message || 'Error al cargar el pago');
      }
    });

    // En vivo: un QR que se paga o una devolución hecha por otro usuario se ven sin recargar
    liveRefresh(5000).pipe(
      filter(() => !!this.payment && !this.saving),
      exhaustMap(() => this.paymentService.getById(this.paymentId, true).pipe(catchError(() => EMPTY))),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(p => (this.payment = p));
  }

  // ========================= DEVOLUCIÓN =========================

  get canRefund(): boolean {
    return !!this.payment && this.payment.refundableAmount > 0 && this.hasPermission('payments.refund');
  }

  openRefund(): void {
    this.refundAmount = this.payment!.refundableAmount;
    this.refundReason = '';
    this.showRefund = true;
  }

  get refundInvalid(): boolean {
    const max = this.payment?.refundableAmount ?? 0;
    const amount = this.refundAmount ?? 0;
    const twoDecimals = Math.round(amount * 100) === amount * 100;
    return amount <= 0 || amount > max || !twoDecimals || !this.refundReason.trim();
  }

  confirmRefund(): void {
    if (this.refundInvalid || !this.payment) return;
    this.saving = true;
    this.paymentService.refund(this.payment.id, { amount: this.refundAmount!, reason: this.refundReason.trim() }).subscribe({
      next: p => {
        this.saving = false;
        this.payment = p;
        this.showRefund = false;
        this.successMessage = `Devolución registrada: S/ ${this.refundAmount!.toFixed(2)}. Entrega el dinero al cliente.`;
      },
      error: error => {
        this.saving = false;
        this.showRefund = false;
        this.errorMessage = error.error?.message || 'No se pudo registrar la devolución';
      }
    });
  }

  // ========================= ANULACIÓN =========================

  get canCancel(): boolean {
    return this.payment?.status === 'PENDING' && this.hasPermission('payments.cancel');
  }

  openCancel(): void {
    this.cancelReason = '';
    this.showCancel = true;
  }

  confirmCancel(): void {
    if (!this.cancelReason.trim() || !this.payment) return;
    this.saving = true;
    this.paymentService.cancel(this.payment.id, this.cancelReason.trim()).subscribe({
      next: p => {
        this.saving = false;
        this.payment = p;
        this.showCancel = false;
        this.successMessage = 'QR anulado: ya no se puede pagar.';
      },
      error: error => {
        this.saving = false;
        this.showCancel = false;
        this.errorMessage = error.error?.message || 'No se pudo anular el QR';
      }
    });
  }

  // ========================= AUXILIARES =========================

  hasPermission(permission: string): boolean {
    return this.authContext.hasPermission(permission);
  }

  get netAmount(): number {
    return this.payment ? this.payment.amount - this.payment.refundedAmount : 0;
  }

  back(): void {
    this.router.navigate(['/payments']);
  }

  statusLabel = paymentStatusLabel;
  statusBadge = paymentStatusBadge;
  statusIcon = paymentStatusIcon;
  conceptLabel = paymentConceptLabel;
  sourceLink = paymentSourceLink;
  channelLabel = channelLabel;
  confirmationLabel = confirmationLabel;

  dismissError(): void { this.errorMessage = null; }
  dismissSuccess(): void { this.successMessage = null; }
}
