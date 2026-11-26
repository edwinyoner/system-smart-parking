// src/app/views/parking-service/transactions/transaction-payment/transaction-payment.component.ts
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { EMPTY, catchError, exhaustMap, filter } from 'rxjs';

import { TransactionService } from '../../../../core/services/parking-service/transaction.service';
import { TransactionDetailResponse } from '../../../../core/models/parking-service/transaction.model';
import { ChargePaymentComponent } from '../../../payment-service/payments/charge-payment/charge-payment.component';
import { liveRefresh } from '../../../../core/utils/live-refresh.util';
import { stayPaymentStatusColor, stayPaymentStatusLabel } from '../../../../core/utils/payment.util';

/**
 * Cobra una estadía antes de que el vehículo salga.
 * El pago lo registra payment-service; parking-service recibe el aviso y la marca pagada.
 * Tras pagar hay un tiempo para salir (exitDeadline) sin que suba el monto.
 */
@Component({
  selector: 'app-transaction-payment',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, ChargePaymentComponent],
  templateUrl: './transaction-payment.component.html',
  styleUrls: ['./transaction-payment.component.css']
})
export class TransactionPaymentComponent implements OnInit {

  searchForm!: FormGroup;
  transaction: TransactionDetailResponse | null = null;
  searching = false;
  searchErrorMessage = '';

  private destroyRef = inject(DestroyRef);

  constructor(
    private fb: FormBuilder,
    private transactionService: TransactionService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.searchForm = this.fb.group({
      plateNumber: ['', [Validators.required, Validators.minLength(6)]]
    });

    this.route.queryParams.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      if (params['transactionId']) {
        this.loadTransaction(+params['transactionId']);
      } else if (params['plateNumber']) {
        this.searchForm.patchValue({ plateNumber: params['plateNumber'] });
        this.searchByPlate();
      }
    });

    // Lo que se debe sube con el tiempo: se recalcula cada 15 s sin spinner
    liveRefresh(15000).pipe(
      filter(() => !!this.transaction && this.transaction.status === 'ACTIVE'),
      exhaustMap(() => this.transactionService.getById(this.transaction!.id, true).pipe(catchError(() => EMPTY))),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(t => (this.transaction = t));
  }

  private loadTransaction(id: number, silent = false): void {
    this.searching = !silent;
    this.transactionService.getById(id, silent).subscribe({
      next: t => {
        this.transaction = t;
        this.searching = false;
      },
      error: error => {
        this.searching = false;
        this.searchErrorMessage = error.error?.message || 'No se pudo cargar la transacción';
      }
    });
  }

  searchByPlate(): void {
    if (this.searchForm.invalid) {
      this.searchForm.markAllAsTouched();
      return;
    }
    this.searching = true;
    this.searchErrorMessage = '';
    this.transaction = null;
    const plateNumber = this.searchForm.getRawValue().plateNumber.trim().toUpperCase();

    this.transactionService.getActiveByPlate(plateNumber).subscribe({
      next: t => {
        this.searching = false;
        if (t) {
          this.transaction = t;
        } else {
          this.searchErrorMessage = 'No se encontró una transacción activa para esta placa';
        }
      },
      error: error => {
        this.searching = false;
        this.searchErrorMessage = error.status === 404
          ? 'No se encontró una transacción activa para esta placa'
          : (error.error?.message || 'Error al buscar la transacción');
      }
    });
  }

  clearSearch(): void {
    this.searchForm.reset();
    this.transaction = null;
    this.searchErrorMessage = '';
  }

  /** payment-service avisa a parking-service casi al instante; se recarga para ver PAID y el plazo */
  onPaid(): void {
    if (this.transaction) this.loadTransaction(this.transaction.id, true);
  }

  goToExit(): void {
    this.router.navigate(['/transactions/exit'], { queryParams: { plateNumber: this.transaction?.vehicle.plateNumber } });
  }

  viewDetail(): void {
    this.router.navigate(['/transactions', this.transaction?.id]);
  }

  cancel(): void {
    this.router.navigate(['/transactions/active']);
  }

  isFieldInvalid(fieldName: string): boolean {
    const field = this.searchForm.get(fieldName);
    return !!(field && field.invalid && (field.dirty || field.touched));
  }

  get amountDue(): number {
    return this.transaction?.payment?.amountDue ?? 0;
  }

  statusLabel = stayPaymentStatusLabel;
  statusColor = stayPaymentStatusColor;
}
