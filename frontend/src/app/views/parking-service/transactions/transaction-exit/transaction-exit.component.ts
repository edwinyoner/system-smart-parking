// src/app/views/parking-service/transactions/transaction-exit/transaction-exit.component.ts
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, exhaustMap, filter } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import {
  CardComponent,
  CardBodyComponent,
  CardHeaderComponent,
  ColComponent,
  RowComponent,
  FormDirective,
  FormControlDirective,
  FormTextDirective,
  ButtonDirective,
  SpinnerComponent,
  AlertComponent,
  BadgeComponent
} from '@coreui/angular';

import { TransactionService } from '../../../../core/services/parking-service/transaction.service';
import { DocumentTypeService } from '../../../../core/services/parking-service/document-type.service';

import { TransactionDetailResponse, TransactionExitRequest } from '../../../../core/models/parking-service/transaction.model';
import { DocumentType } from '../../../../core/models/parking-service/document-type.model';
import { ChargePaymentComponent } from '../../../payment-service/payments/charge-payment/charge-payment.component';
import { liveRefresh } from '../../../../core/utils/live-refresh.util';
import { stayPaymentStatusColor, stayPaymentStatusLabel } from '../../../../core/utils/payment.util';

@Component({
  selector: 'app-transaction-exit',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    CardComponent,
    CardBodyComponent,
    CardHeaderComponent,
    ColComponent,
    RowComponent,
    FormDirective,
    FormControlDirective,
    FormTextDirective,
    ButtonDirective,
    SpinnerComponent,
    AlertComponent,
    BadgeComponent,
    ChargePaymentComponent
  ],
  templateUrl: './transaction-exit.component.html',
  styleUrls: ['./transaction-exit.component.css']
})
export class TransactionExitComponent implements OnInit {
  
  searchForm!: FormGroup;
  exitForm!: FormGroup;
  
  // ========================= DATOS =========================
  transaction: TransactionDetailResponse | null = null;
  documentTypes: DocumentType[] = [];
  
  // ========================= ESTADOS =========================
  searching = false;
  submitting = false;
  loadingDocumentTypes = false;
  
  // ========================= MENSAJES =========================
  errorMessage = '';
  searchErrorMessage = '';

  private destroyRef = inject(DestroyRef);
  
  constructor(
    private fb: FormBuilder,
    private transactionService: TransactionService,
    private documentTypeService: DocumentTypeService,
    private router: Router,
    private route: ActivatedRoute
  ) {}
  
  ngOnInit(): void {
    this.buildForms();
    this.loadDocumentTypes();
    this.checkQueryParams();

    // Lo que se debe sube con el tiempo: se recalcula cada 15 s sin spinner
    liveRefresh(15000).pipe(
      filter(() => !!this.transaction && !this.submitting),
      exhaustMap(() => this.transactionService.getById(this.transaction!.id, true).pipe(catchError(() => EMPTY))),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(t => (this.transaction = t));
  }

  /** Lo que falta pagar para poder salir */
  get amountDue(): number {
    return this.transaction?.payment?.amountDue ?? 0;
  }

  /** Tras el pago, parking-service ya recibió el aviso: se recarga para habilitar la salida */
  onPaid(): void {
    if (!this.transaction) return;
    this.transactionService.getById(this.transaction.id, true).subscribe(t => (this.transaction = t));
  }
  
  // ========================= CONSTRUCCIÓN DE FORMULARIOS =========================
  
  buildForms(): void {
    // Formulario de búsqueda
    this.searchForm = this.fb.group({
      plateNumber: ['', [Validators.required, Validators.minLength(6)]]
    });
    
    // Formulario de salida
    this.exitForm = this.fb.group({
      exitDocumentTypeId: [null, Validators.required],
      exitDocumentNumber: ['', [Validators.required, Validators.minLength(8)]],
      notes: ['']
    });
  }
  
  checkQueryParams(): void {
    // Si viene plateNumber por queryParams, buscarlo automáticamente
    this.route.queryParams.subscribe(params => {
      if (params['plateNumber']) {
        this.searchForm.patchValue({ plateNumber: params['plateNumber'] });
        this.searchByPlate();
      }
    });
  }
  
  // ========================= CARGA DE DATOS =========================
  
  loadDocumentTypes(): void {
    this.loadingDocumentTypes = true;
    this.documentTypeService.getActive().subscribe({
      next: (documentTypes) => {
        this.documentTypes = documentTypes;
        this.loadingDocumentTypes = false;
      },
      error: (error) => {
        console.error('Error loading document types:', error);
        this.loadingDocumentTypes = false;
      }
    });
  }
  
  // ========================= BÚSQUEDA DE TRANSACCIÓN =========================
  
  searchByPlate(): void {
    if (this.searchForm.invalid) {
      this.markFormGroupTouched(this.searchForm);
      return;
    }
    
    this.searching = true;
    this.searchErrorMessage = '';
    this.transaction = null;
    
    const plateNumber = this.searchForm.value.plateNumber.toUpperCase();
    
    this.transactionService.getActiveByPlate(plateNumber).subscribe({
      next: (transaction) => {
        if (transaction) {
          this.transaction = transaction;
          this.searching = false;
        } else {
          this.searchErrorMessage = 'No se encontró una transacción activa para esta placa';
          this.searching = false;
        }
      },
      error: (error) => {
        console.error('Error searching transaction:', error);
        this.searchErrorMessage = error.error?.message || 'Error al buscar la transacción';
        this.searching = false;
      }
    });
  }
  
  clearSearch(): void {
    this.searchForm.reset();
    this.transaction = null;
    this.searchErrorMessage = '';
    this.exitForm.reset();
  }
  
  // ========================= REGISTRO DE SALIDA =========================
  
  onSubmit(): void {
    if (this.exitForm.invalid || !this.transaction) {
      this.markFormGroupTouched(this.exitForm);
      return;
    }
    
    this.submitting = true;
    this.errorMessage = '';
    
    const operatorId = this.getLoggedUserId();
    
    const request: TransactionExitRequest = {
      transactionId: this.transaction.id,
      exitDocumentTypeId: this.exitForm.value.exitDocumentTypeId,
      exitDocumentNumber: this.exitForm.value.exitDocumentNumber,
      operatorId,
      exitMethod: 'MANUAL',
      notes: this.exitForm.value.notes || undefined
    };
    
    this.transactionService.recordExit(request).subscribe({
      next: (response) => {
        // Navegar al detalle de la transacción
        this.router.navigate(['/transactions', response.id]);
      },
      error: (error) => {
        // 409 "Falta pagar...": el monto subió (se pasó del tiempo para salir); se recarga para cobrar la diferencia
        this.errorMessage = error.error?.message || 'Error al registrar la salida';
        this.submitting = false;
        if (error.status === 409) this.onPaid();
      }
    });
  }
  
  // ========================= HELPERS =========================
  
  getLoggedUserId(): number {
    // TODO: Obtener del servicio de autenticación
    return 1; // Placeholder
  }
  
  markFormGroupTouched(formGroup: FormGroup): void {
    Object.keys(formGroup.controls).forEach(key => {
      const control = formGroup.get(key);
      control?.markAsTouched();
      
      if (control instanceof FormGroup) {
        this.markFormGroupTouched(control);
      }
    });
  }
  
  isFieldInvalid(formGroup: FormGroup, fieldName: string): boolean {
    const field = formGroup.get(fieldName);
    return !!(field && field.invalid && (field.dirty || field.touched));
  }
  
  cancel(): void {
    this.router.navigate(['/transactions/active']);
  }
  
  paymentStatusLabel = stayPaymentStatusLabel;
  paymentStatusColor = stayPaymentStatusColor;
}