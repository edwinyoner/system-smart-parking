// src/app/views/parking-service/transactions/transaction-detail/transaction-detail.component.ts
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, exhaustMap, filter } from 'rxjs';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  CardComponent,
  CardBodyComponent,
  CardHeaderComponent,
  ColComponent,
  RowComponent,
  ButtonDirective,
  BadgeComponent,
  SpinnerComponent,
  AlertComponent
} from '@coreui/angular';

import { TransactionService } from '../../../../core/services/parking-service/transaction.service';
import { InfractionService } from '../../../../core/services/parking-service/infraction.service';
import { Infraction } from '../../../../core/models/parking-service/infraction.model';
import { infractionStatusInfo, infractionTypeLabel } from '../../../../core/utils/infraction.util';
import { TransactionDetailResponse } from '../../../../core/models/parking-service/transaction.model';
import { PaymentService } from '../../../../core/services/payment-service/payment.service';
import { Payment } from '../../../../core/models/payment-service/payment.model';
import {
  paymentStatusBadge, paymentStatusLabel, stayPaymentStatusColor, stayPaymentStatusLabel,
} from '../../../../core/utils/payment.util';
import { PermissionService } from '../../../../core/services/auth-service/permission.service';
import { liveRefresh } from '../../../../core/utils/live-refresh.util';
import { confidencePercent } from '../../../../core/utils/plate.util';
import { apiError } from '../../../../core/utils/parking.util';
import { StayAction, StayActionModalComponent, StayRef } from '../shared/stay-action-modal.component';

@Component({
  selector: 'app-transaction-detail',
  standalone: true,
  imports: [
    RouterLink,
    CommonModule,
    FormsModule,
    StayActionModalComponent,
    CardComponent,
    CardBodyComponent,
    CardHeaderComponent,
    ColComponent,
    RowComponent,
    ButtonDirective,
    BadgeComponent,
    SpinnerComponent,
    AlertComponent
  ],
  templateUrl: './transaction-detail.component.html',
  styleUrls: ['./transaction-detail.component.css']
})
export class TransactionDetailComponent implements OnInit {
  
  transaction: TransactionDetailResponse | null = null;
  loading = false;
  errorMessage = '';
  /** Pagos de esta estadía en payment-service */
  payments: Payment[] = [];
  /** Infracciones registradas en esta estadía */
  infractions: Infraction[] = [];
  successMessage = '';

  // Acciones sobre la estadía en curso
  action: StayAction | null = null;
  actionStay: StayRef | null = null;

  // Observaciones editables
  editingNotes = false;
  notesDraft = '';
  savingNotes = false;

  private readonly permissions = inject(PermissionService);
  private readonly destroyRef = inject(DestroyRef);
  readonly canExit = this.permissions.hasPermission('transactions.exit');
  readonly canMove = this.permissions.hasPermission('transactions.entry');
  readonly canCancel = this.permissions.hasPermission('transactions.cancel');
  readonly canPay = this.permissions.hasPermission('payments.process');
  readonly canEditNotes = this.permissions.hasAnyPermission(['transactions.entry', 'transactions.exit']);
  readonly confidencePercent = confidencePercent;
  readonly canSeeInfractions = this.permissions.hasPermission('infractions.read');
  readonly canCreateInfraction = this.permissions.hasPermission('infractions.create');
  readonly infractionStatus = infractionStatusInfo;
  readonly infractionType = infractionTypeLabel;
  private readonly infractionService = inject(InfractionService);
  
  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private transactionService: TransactionService,
    private paymentService: PaymentService
  ) {}
  
  ngOnInit(): void {
    const id = this.route.snapshot.params['id'];
    if (id) {
      this.loadTransaction(+id);
    }

    // En curso: el tiempo y lo que se debe cambian; se recarga cada 10 s sin spinner
    liveRefresh(10000).pipe(
      filter(() => this.transaction?.status === 'ACTIVE' && !this.editingNotes),
      exhaustMap(() => this.transactionService.getById(this.transaction!.id, true).pipe(catchError(() => EMPTY))),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(t => {
      const paidBefore = this.transaction?.payment?.paidAmount;
      this.transaction = t;
      if (t.payment?.paidAmount !== paidBefore) this.loadPayments(t.id);
    });
  }

  private loadInfractions(id: number): void {
    if (!this.canSeeInfractions) return;
    this.infractionService.getByTransaction(id, true).subscribe({
      next: list => (this.infractions = list),
      error: () => (this.infractions = [])
    });
  }

  private loadPayments(id: number): void {
    this.paymentService.getBySource('PARKING_STAY', id, true).subscribe({
      next: payments => (this.payments = payments),
      error: () => (this.payments = []),   // sin permiso de pagos: solo se ve el resumen
    });
  }

  // ========================= ACCIONES =========================

  open(action: StayAction): void {
    const t = this.transaction;
    if (!t) return;
    this.actionStay = {
      id: t.id, plateNumber: t.vehicle.plateNumber, zoneId: t.zone.id, zoneName: t.zone.name,
      spaceCode: t.space.code, paidAmount: t.payment?.paidAmount ?? 0
    };
    this.action = action;
  }

  closeAction(): void {
    this.action = null;
    this.actionStay = null;
  }

  onActionDone(message: string): void {
    this.successMessage = message;
    if (this.transaction) {
      this.transactionService.getById(this.transaction.id, true).subscribe(t => (this.transaction = t));
    }
  }

  startNotes(): void {
    this.notesDraft = this.transaction?.notes ?? '';
    this.editingNotes = true;
  }

  saveNotes(): void {
    if (!this.transaction || this.savingNotes) return;
    this.savingNotes = true;
    const notes = this.notesDraft.trim() || null;
    this.transactionService.updateNotes(this.transaction.id, notes).subscribe({
      next: () => {
        this.savingNotes = false;
        this.editingNotes = false;
        this.transaction = { ...this.transaction!, notes: notes ?? undefined };
        this.successMessage = 'Observaciones guardadas';
      },
      error: err => {
        this.savingNotes = false;
        this.errorMessage = apiError(err, 'No se pudieron guardar las observaciones');
      }
    });
  }

  methodLabel(method?: string | null, confidence?: number): string {
    if (!method) return '-';
    return method === 'CAMERA_AI' ? `Cámara (lectura ${confidencePercent(confidence)})` : 'Manual (operador)';
  }
  
  // ========================= CARGA DE DATOS =========================
  
  loadTransaction(id: number): void {
    this.loading = true;
    this.transactionService.getById(id).subscribe({
      next: (transaction) => {
        this.transaction = transaction;
        this.loading = false;
        this.loadPayments(id);
        this.loadInfractions(id);
      },
      error: (error) => {
        this.errorMessage = apiError(error, 'No se pudo cargar la estadía');
        this.loading = false;
      }
    });
  }
  
  // ========================= NAVEGACIÓN =========================
  
  goBack(): void {
    this.router.navigate(['/transactions']);
  }
  
  goToProcessPayment(): void {
    if (this.transaction && this.transaction.status === 'ACTIVE') {
      this.router.navigate(['/transactions/payment'], {
        queryParams: { transactionId: this.transaction.id }
      });
    }
  }
  
  goToRegisterExit(): void {
    if (this.transaction && this.transaction.status === 'ACTIVE') {
      this.router.navigate(['/transactions/exit'], {
        queryParams: { transactionId: this.transaction.id }
      });
    }
  }
  
  // ========================= HELPERS =========================
  
  getStatusBadge(status: string): string {
    switch (status) {
      case 'ACTIVE': return 'warning';
      case 'COMPLETED': return 'success';
      case 'CANCELLED': return 'danger';
      default: return 'secondary';
    }
  }
  
  getStatusText(status: string): string {
    switch (status) {
      case 'ACTIVE': return 'En curso';
      case 'COMPLETED': return 'Completada';
      case 'CANCELLED': return 'Cancelada';
      default: return status;
    }
  }
  
  getPaymentStatusBadge = stayPaymentStatusColor;
  getPaymentStatusText = stayPaymentStatusLabel;
  paymentBadge = paymentStatusBadge;
  paymentLabel = paymentStatusLabel;
}