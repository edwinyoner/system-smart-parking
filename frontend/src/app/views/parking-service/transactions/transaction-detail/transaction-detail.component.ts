// src/app/views/parking-service/transactions/transaction-detail/transaction-detail.component.ts
import { Component, OnInit } from '@angular/core';
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
import { TransactionDetailResponse } from '../../../../core/models/parking-service/transaction.model';
import { PaymentService } from '../../../../core/services/payment-service/payment.service';
import { Payment } from '../../../../core/models/payment-service/payment.model';
import {
  paymentStatusBadge, paymentStatusLabel, stayPaymentStatusColor, stayPaymentStatusLabel,
} from '../../../../core/utils/payment.util';

@Component({
  selector: 'app-transaction-detail',
  standalone: true,
  imports: [
    RouterLink,
    CommonModule,
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
  }
  
  // ========================= CARGA DE DATOS =========================
  
  loadTransaction(id: number): void {
    this.loading = true;
    this.transactionService.getById(id).subscribe({
      next: (transaction) => {
        this.transaction = transaction;
        this.loading = false;
        this.paymentService.getBySource('PARKING_STAY', id, true).subscribe({
          next: payments => (this.payments = payments),
          error: () => (this.payments = []),   // sin permiso de pagos: solo se ve el resumen
        });
      },
      error: (error) => {
        console.error('Error loading transaction:', error);
        this.errorMessage = 'No se pudo cargar la transacción';
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
        queryParams: { plateNumber: this.transaction.vehicle.plateNumber }
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
      case 'ACTIVE': return 'Activa';
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