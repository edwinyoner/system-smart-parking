import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { PaymentTypeService } from '../../../../core/services/payment-service/payment-type.service';
import { PaymentType } from '../../../../core/models/payment-service/payment-type.model';
import { PaymentTypeConfigComponent } from '../payment-type-config/payment-type-config.component';
import { paymentChannelsValidator } from '../../../../core/utils/payment-type.util';
import { AlertMessageComponent } from '../../../../shared/components/alert-message/alert-message.component';

@Component({
  selector: 'app-payment-type-edit',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, AlertMessageComponent, PaymentTypeConfigComponent],
  templateUrl: './payment-type-edit.component.html',
  styleUrls: ['./payment-type-edit.component.css']
})
export class PaymentTypeEditComponent implements OnInit {

  paymentTypeId!: number;
  form!: FormGroup;
  isLoading = true;
  isSubmitting = false;
  errorMessage: string | null = null;
  currentPaymentType: PaymentType | null = null;

  constructor(
    private fb: FormBuilder,
    private paymentTypeService: PaymentTypeService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.paymentTypeId = Number(this.route.snapshot.paramMap.get('id'));
    this.initForm();
    this.loadPaymentType();
  }

  private initForm(): void {
    this.form = this.fb.group({
      code:        [{ value: '', disabled: true }],   // no se edita; lo usa la prueba del número
      name:        ['', [Validators.required, Validators.maxLength(100)]],
      description: ['', Validators.maxLength(255)],
      confirmationMode: ['AUTOMATIC', Validators.required],
      requiresReference: [false],
      channels:    [['EXIT_SCREEN', 'STREET_KIOSK', 'MOBILE_APP', 'OPERATOR']],
      status:      [true]
    }, { validators: paymentChannelsValidator });
  }

  private loadPaymentType(): void {
    this.isLoading = true;
    this.paymentTypeService.getById(this.paymentTypeId).subscribe({
      next: (dt: PaymentType) => {
        this.currentPaymentType = dt;
        this.form.patchValue({
          code: dt.code,
          name: dt.name,
          description: dt.description,
          confirmationMode: dt.confirmationMode,
          requiresReference: dt.requiresReference,
          channels: dt.channels,
          status: dt.status
        });
        this.isLoading = false;
      },
      error: (err) => {
        this.isLoading    = false;
        this.errorMessage = 'Error al cargar el tipo de pago';
        console.error(err);
      }
    });
  }

  onSubmit(): void {
    if (this.form.invalid) { this.markTouched(this.form); return; }
    this.isSubmitting = true;
    this.errorMessage = null;

    this.paymentTypeService.update(this.paymentTypeId, this.form.value).subscribe({
      next: (res) => {
        this.isSubmitting = false;
        this.router.navigate(['/payment-types'], {
          state: { successMessage: `Tipo de pago "${res.name}" actualizado exitosamente` }
        });
      },
      error: (err) => {
        this.isSubmitting = false;
        this.errorMessage = err.error?.message || 'Error al actualizar el tipo de pago';
        window.scrollTo({ top: 0, behavior: 'smooth' });
      }
    });
  }

  /** Desactivado deja de ofrecerse en todos sus canales */
  get deactivationWarning(): boolean {
    return !!this.currentPaymentType?.status && !this.form.get('status')?.value;
  }

  cancel(): void { this.router.navigate(['/payment-types']); }

  isFieldInvalid(f: string): boolean {
    const c = this.form.get(f);
    return !!(c && c.invalid && (c.dirty || c.touched));
  }

  getFieldError(f: string): string {
    const c = this.form.get(f);
    if (c?.errors?.['required'])   return 'Este campo es obligatorio';
    if (c?.errors?.['maxlength'])  return `Máximo ${c.errors['maxlength'].requiredLength} caracteres`;
    return '';
  }

  private markTouched(fg: FormGroup): void {
    Object.keys(fg.controls).forEach(k => fg.get(k)?.markAsTouched());
  }

  dismissError(): void { this.errorMessage = null; }
}