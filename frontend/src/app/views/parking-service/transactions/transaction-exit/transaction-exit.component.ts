// src/app/views/parking-service/transactions/transaction-exit/transaction-exit.component.ts
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EMPTY, Observable, catchError, exhaustMap, filter } from 'rxjs';
import {
  CardComponent,
  CardBodyComponent,
  CardHeaderComponent,
  ColComponent,
  RowComponent,
  FormDirective,
  FormControlDirective,
  FormLabelDirective,
  FormSelectDirective,
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
import { confidencePercent, normalizePlate, plateValidator } from '../../../../core/utils/plate.util';
import { describeLength, documentNumberError, normalizeDocumentNumber } from '../../../../core/utils/document-type.util';
import { apiError } from '../../../../core/utils/parking.util';

/**
 * Salida manual de un vehículo (respaldo del operador cuando la cámara falla).
 *
 * 1. Se busca la estadía en curso por placa (o llega con ?transactionId= / ?plateNumber=).
 * 2. Si falta pagar, se cobra aquí mismo (QR o efectivo); sin pagar no hay salida.
 * 3. El documento solo se pide si el parqueo lo exige Y se entró con documento (anti-robo);
 *    si se entró solo con la placa, sale con la placa.
 * Lo que se debe sube con el tiempo: la estadía se recalcula cada 15 s sin spinner.
 * Al registrar, se queda en la pantalla lista para la siguiente salida.
 */
@Component({
  selector: 'app-transaction-exit',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    CardComponent,
    CardBodyComponent,
    CardHeaderComponent,
    ColComponent,
    RowComponent,
    FormDirective,
    FormControlDirective,
    FormLabelDirective,
    FormSelectDirective,
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

  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly transactionService = inject(TransactionService);
  private readonly documentTypeService = inject(DocumentTypeService);

  searchForm!: FormGroup;
  exitForm!: FormGroup;

  transaction: TransactionDetailResponse | null = null;
  documentTypes: DocumentType[] = [];

  searching = false;
  submitting = false;
  errorMessage = '';
  searchErrorMessage = '';
  lastExit: TransactionDetailResponse | null = null;

  readonly paymentStatusLabel = stayPaymentStatusLabel;
  readonly paymentStatusColor = stayPaymentStatusColor;
  readonly confidencePercent = confidencePercent;

  ngOnInit(): void {
    this.searchForm = this.fb.group({
      plateNumber: ['', [Validators.required, plateValidator]]
    });
    this.exitForm = this.fb.group({
      exitDocumentTypeId: [null],
      exitDocumentNumber: [''],
      notes: ['', Validators.maxLength(500)]
    });

    this.documentTypeService.getActive()
      .pipe(catchError(() => EMPTY), takeUntilDestroyed(this.destroyRef))
      .subscribe(types => {
        this.documentTypes = types;
        this.prepareDocument();
      });

    // Desde "Vehículos dentro" se llega con la estadía o la placa ya elegidas
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      const id = Number(params.get('transactionId'));
      const plate = params.get('plateNumber');
      if (id) {
        this.load(this.transactionService.getById(id), 'No se encontró la estadía');
      } else if (plate) {
        this.searchForm.patchValue({ plateNumber: plate });
        this.searchByPlate();
      }
    });

    liveRefresh(15000).pipe(
      filter(() => !!this.transaction && this.transaction.status === 'ACTIVE' && !this.submitting),
      exhaustMap(() => this.transactionService.getById(this.transaction!.id, true).pipe(catchError(() => EMPTY))),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(t => (this.transaction = t));
  }

  // ========================= BÚSQUEDA =========================

  onPlateInput(): void {
    const ctrl = this.searchForm.get('plateNumber')!;
    const upper = (ctrl.value ?? '').toUpperCase();
    if (upper !== ctrl.value) {
      ctrl.setValue(upper, { emitEvent: false });
    }
  }

  searchByPlate(): void {
    this.searchForm.markAllAsTouched();
    if (this.searchForm.invalid) {
      return;
    }
    const plate = normalizePlate(this.searchForm.value.plateNumber);
    this.load(this.transactionService.getActiveByPlate(plate), `No hay una estadía en curso para ${plate}`);
  }

  private load(source: Observable<TransactionDetailResponse>, notFound: string): void {
    this.searching = true;
    this.searchErrorMessage = '';
    this.errorMessage = '';
    this.transaction = null;
    source.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: t => {
        this.searching = false;
        if (t.status !== 'ACTIVE') {
          this.searchErrorMessage = `La estadía #${t.id} ya no está en curso`;
          return;
        }
        this.transaction = t;
        this.prepareDocument();
      },
      error: err => {
        this.searching = false;
        this.searchErrorMessage = err?.status === 404 ? notFound : apiError(err, 'No se pudo buscar la estadía');
      }
    });
  }

  clearSearch(): void {
    this.searchForm.reset({ plateNumber: '' });
    this.exitForm.reset({ exitDocumentTypeId: null, exitDocumentNumber: '', notes: '' });
    this.transaction = null;
    this.searchErrorMessage = '';
    this.errorMessage = '';
  }

  // ========================= PAGO =========================

  /** Lo que falta pagar para poder salir */
  get amountDue(): number {
    return this.transaction?.payment?.amountDue ?? 0;
  }

  /** Tras el pago, parking-service ya recibió el aviso: se recarga para habilitar la salida */
  onPaid(): void {
    if (!this.transaction) return;
    this.transactionService.getById(this.transaction.id, true)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(t => (this.transaction = t));
  }

  // ========================= DOCUMENTO (ANTI-ROBO) =========================

  /** Solo si el parqueo lo exige y se entró con documento */
  get documentRequired(): boolean {
    return !!this.transaction?.parking.requireDriverDocument && !!this.transaction?.entryDocument;
  }

  /** "DNI terminado en 678": recuerda qué pedir sin mostrar el número completo */
  get entryDocumentHint(): string {
    const doc = this.transaction?.entryDocument;
    if (!doc) return '';
    return `${doc.type ?? 'Documento'} terminado en ${doc.number.slice(-3)}`;
  }

  get selectedDocumentType(): DocumentType | undefined {
    return this.documentTypes.find(d => d.id === this.exitForm.get('exitDocumentTypeId')?.value);
  }

  get documentError(): string | null {
    const type = this.selectedDocumentType;
    if (!this.documentRequired || !type) return null;
    return documentNumberError(type, this.exitForm.get('exitDocumentNumber')?.value ?? '');
  }

  get documentHint(): string {
    const type = this.selectedDocumentType;
    return type ? `${type.code}: ${describeLength(type)}` : '';
  }

  /** Validadores según el caso y el tipo de la entrada ya elegido */
  private prepareDocument(): void {
    const type = this.exitForm.get('exitDocumentTypeId')!;
    const number = this.exitForm.get('exitDocumentNumber')!;
    if (this.documentRequired) {
      type.setValidators(Validators.required);
      number.setValidators(Validators.required);
      const entryType = this.documentTypes.find(d => d.code === this.transaction?.entryDocument?.type);
      if (entryType && !type.value) {
        type.setValue(entryType.id, { emitEvent: false });
      }
    } else {
      type.clearValidators();
      number.clearValidators();
    }
    type.updateValueAndValidity({ emitEvent: false });
    number.updateValueAndValidity({ emitEvent: false });
  }

  // ========================= SALIDA =========================

  get canSubmit(): boolean {
    return !!this.transaction && this.amountDue === 0 && this.exitForm.valid && !this.documentError && !this.submitting;
  }

  onSubmit(): void {
    this.exitForm.markAllAsTouched();
    if (!this.canSubmit || !this.transaction) {
      return;
    }
    const v = this.exitForm.getRawValue();
    const request: TransactionExitRequest = {
      transactionId: this.transaction.id,
      exitDocumentTypeId: this.documentRequired ? v.exitDocumentTypeId : null,
      exitDocumentNumber: this.documentRequired ? normalizeDocumentNumber(v.exitDocumentNumber) : null,
      notes: (v.notes ?? '').trim() || null
    };

    this.submitting = true;
    this.errorMessage = '';
    this.transactionService.recordExit(request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: done => {
          this.submitting = false;
          this.lastExit = done;
          this.clearSearch();
        },
        error: err => {
          this.submitting = false;
          this.errorMessage = apiError(err, 'No se pudo registrar la salida');
          // 409 "Falta pagar": se pasó del tiempo para salir; se recarga para cobrar la diferencia
          if (err?.status === 409) this.onPaid();
        }
      });
  }

  isFieldInvalid(form: FormGroup, name: string): boolean {
    const field = form.get(name);
    return !!(field && field.invalid && (field.dirty || field.touched));
  }

  cancel(): void {
    this.router.navigate(['/transactions/active']);
  }
}
