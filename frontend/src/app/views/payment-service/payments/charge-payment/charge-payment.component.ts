import {
  Component, DestroyRef, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges, inject,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { EMPTY, Subscription, catchError, exhaustMap, filter, interval, timer } from 'rxjs';

import { PaymentService } from '../../../../core/services/payment-service/payment.service';
import { PaymentTypeService } from '../../../../core/services/payment-service/payment-type.service';
import { AuthContextService } from '../../../../core/services/auth-service/auth-context.service';
import { Payment, PaymentConcept } from '../../../../core/models/payment-service/payment.model';
import { PaymentType } from '../../../../core/models/payment-service/payment-type.model';
import { formatCountdown, secondsUntil } from '../../../../core/utils/payment.util';

/**
 * Cobra una estadía o una multa desde el panel (canal OPERATOR).
 *
 * - El monto lo decide parking-service; aquí solo se muestra (amountDue).
 * - Efectivo (manual): el pago nace COMPLETED.
 * - QR (automático): el pago nace PENDING con su QR; se consulta cada 3 s hasta que la
 *   pasarela lo confirme, lo rechace o venza. Si al abrir ya hay un QR vigente, se retoma.
 *
 * Emite (paid) cuando un pago queda COMPLETED: quien lo usa recarga la estadía o la multa.
 */
@Component({
  selector: 'app-charge-payment',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './charge-payment.component.html',
  styleUrls: ['./charge-payment.component.css'],
})
export class ChargePaymentComponent implements OnInit, OnChanges {

  @Input({ required: true }) concept!: PaymentConcept;
  @Input({ required: true }) sourceId!: number;
  /** Lo que falta pagar según parking-service */
  @Input() amountDue = 0;
  @Input() currency = 'PEN';

  @Output() paid = new EventEmitter<Payment>();

  paymentTypes: PaymentType[] = [];
  selectedType: PaymentType | null = null;
  operationNumber = '';
  /** Solo para calcular el vuelto en efectivo; no se envía */
  amountReceived: number | null = null;

  /** QR pendiente que se está mostrando */
  pending: Payment | null = null;
  secondsLeft = 0;

  loadingTypes = false;
  submitting = false;
  simulating = false;
  errorMessage = '';
  infoMessage = '';
  successMessage = '';

  private destroyRef = inject(DestroyRef);
  private polling?: Subscription;

  constructor(
    private paymentService: PaymentService,
    private paymentTypeService: PaymentTypeService,
    private authContext: AuthContextService,
  ) {}

  ngOnInit(): void {
    this.loadPaymentTypes();
    // Cuenta regresiva del QR
    interval(1000).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      if (this.pending) this.secondsLeft = secondsUntil(this.pending.expiresAt);
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['sourceId'] || changes['concept']) {
      this.stopPolling();
      this.pending = null;
      this.successMessage = '';
      this.errorMessage = '';
      if (this.sourceId) this.resumePendingQr();
    }
  }

  // ========================= CARGA =========================

  private loadPaymentTypes(): void {
    this.loadingTypes = true;
    // Desde el panel cobra un operador: solo los tipos que se ofrecen con operador
    this.paymentTypeService.getActive('OPERATOR').subscribe({
      next: types => {
        this.paymentTypes = types;
        this.loadingTypes = false;
        if (types.length === 1) this.selectType(types[0]);
      },
      error: error => {
        this.loadingTypes = false;
        this.errorMessage = error.error?.message || 'No se pudieron cargar los tipos de pago';
      },
    });
  }

  /** Si ya hay un QR vigente para este cobro (otra pestaña, recarga), se sigue mostrando */
  private resumePendingQr(): void {
    this.paymentService.getBySource(this.concept, this.sourceId, true).subscribe({
      next: payments => {
        const open = payments.find(p => p.status === 'PENDING' && secondsUntil(p.expiresAt) > 0);
        if (open) this.showPending(open);
      },
      error: () => { /* sin permiso payments.view o servicio caído: se cobra igual */ },
    });
  }

  // ========================= COBRO =========================

  selectType(type: PaymentType): void {
    if (this.pending) return;
    this.selectedType = type;
    this.operationNumber = '';
    this.errorMessage = '';
  }

  get isManual(): boolean {
    return this.selectedType?.confirmationMode === 'MANUAL';
  }

  get change(): number {
    return this.amountReceived && this.amountReceived > this.amountDue ? this.amountReceived - this.amountDue : 0;
  }

  get canSubmit(): boolean {
    if (!this.selectedType || this.submitting || this.pending || this.amountDue <= 0) return false;
    if (this.isManual && this.selectedType.requiresReference && !this.operationNumber.trim()) return false;
    if (this.isManual && this.amountReceived !== null && this.amountReceived < this.amountDue) return false;
    return this.hasPermission('payments.process');
  }

  submit(): void {
    if (!this.canSubmit || !this.selectedType) return;
    this.submitting = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.paymentService.create({
      concept: this.concept,
      sourceId: this.sourceId,
      paymentTypeId: this.selectedType.id!,
      channel: 'OPERATOR',
      operationNumber: this.isManual ? this.operationNumber.trim() || undefined : undefined,
    }).subscribe({
      next: payment => {
        this.submitting = false;
        if (payment.status === 'COMPLETED') {
          this.finishPaid(payment);
        } else if (payment.status === 'PENDING') {
          this.showPending(payment);
        }
      },
      error: error => {
        this.submitting = false;
        // 409: parking-service dice que no se debe nada (ya pagado, sin costo o ya salió)
        this.errorMessage = error.error?.message || 'No se pudo registrar el pago';
      },
    });
  }

  // ========================= QR PENDIENTE =========================

  private showPending(payment: Payment): void {
    this.pending = payment;
    this.secondsLeft = secondsUntil(payment.expiresAt);
    this.infoMessage = '';
    this.startPolling(payment.id);
  }

  /** Cada 3 s pregunta si el QR ya se pagó (la pasarela avisa a payment-service por webhook) */
  private startPolling(paymentId: number): void {
    this.stopPolling();
    this.polling = timer(3000, 3000).pipe(
      exhaustMap(() => this.paymentService.getById(paymentId, true).pipe(catchError(() => EMPTY))),
      filter(p => p.status !== 'PENDING' || secondsUntil(p.expiresAt) === 0),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(p => this.onPendingResolved(p));
  }

  private stopPolling(): void {
    this.polling?.unsubscribe();
    this.polling = undefined;
  }

  private onPendingResolved(payment: Payment): void {
    switch (payment.status) {
      case 'COMPLETED':
        this.finishPaid(payment);
        return;
      case 'FAILED':
        this.closePending(`La pasarela rechazó el pago${payment.failureReason ? ': ' + payment.failureReason : ''}. Genera otro QR o cobra en efectivo.`);
        return;
      case 'CANCELLED':
        this.closePending('El QR fue anulado.');
        return;
      default:
        // EXPIRED, o PENDING con el tiempo agotado (el vencimiento lo marca el servidor cada minuto)
        this.closePending('El QR venció sin pagarse. Genera uno nuevo.');
    }
  }

  private closePending(message: string): void {
    this.stopPolling();
    this.pending = null;
    this.infoMessage = message;
  }

  private finishPaid(payment: Payment): void {
    this.stopPolling();
    this.pending = null;
    this.amountReceived = null;
    this.operationNumber = '';
    this.successMessage = `Pago registrado: ${payment.currency === 'PEN' ? 'S/' : payment.currency} ${payment.amount.toFixed(2)} (${payment.paymentTypeName}).`;
    this.paid.emit(payment);
  }

  cancelQr(): void {
    if (!this.pending) return;
    const id = this.pending.id;
    this.paymentService.cancel(id, 'Anulado por el operador').subscribe({
      next: () => this.closePending('QR anulado. Puedes cobrar de otra forma.'),
      error: error => (this.errorMessage = error.error?.message || 'No se pudo anular el QR'),
    });
  }

  /** Pasarela de prueba: hace lo que haría la app del banco al pagar o rechazar el QR */
  simulate(approve: boolean): void {
    const ref = this.pending?.gatewayReference;
    if (!ref) return;
    this.simulating = true;
    const call = approve ? this.paymentService.simulatePay(ref) : this.paymentService.simulateReject(ref);
    call.subscribe({
      next: () => {
        this.simulating = false;
        // El webhook llega casi al instante: se consulta ya, sin esperar los 3 s
        this.paymentService.getById(this.pending!.id, true).subscribe(p => {
          if (p.status !== 'PENDING') this.onPendingResolved(p);
        });
      },
      error: error => {
        this.simulating = false;
        this.errorMessage = error.error?.message || 'La pasarela de prueba no respondió';
      },
    });
  }

  // ========================= AUXILIARES =========================

  hasPermission(permission: string): boolean {
    return this.authContext.hasPermission(permission);
  }

  get currencySymbol(): string {
    return this.currency === 'PEN' ? 'S/' : this.currency;
  }

  countdown(): string {
    return formatCountdown(this.secondsLeft);
  }
}
