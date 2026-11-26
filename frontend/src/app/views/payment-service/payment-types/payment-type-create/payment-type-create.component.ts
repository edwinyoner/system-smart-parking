import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { PaymentTypeService } from '../../../../core/services/payment-service/payment-type.service';
import { PaymentTypeConfigComponent } from '../payment-type-config/payment-type-config.component';
import { paymentChannelsValidator } from '../../../../core/utils/payment-type.util';
import { AlertMessageComponent } from '../../../../shared/components/alert-message/alert-message.component';

@Component({
  selector: 'app-payment-type-create',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, AlertMessageComponent, PaymentTypeConfigComponent],
  templateUrl: './payment-type-create.component.html',
  styleUrls: ['./payment-type-create.component.css']
})
export class PaymentTypeCreateComponent implements OnInit {

  form!: FormGroup;
  isSubmitting = false;
  errorMessage: string | null = null;

  constructor(
    private fb: FormBuilder,
    private paymentTypeService: PaymentTypeService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      code:        ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9_]{2,20}$/)]],
      name:        ['', [Validators.required, Validators.maxLength(100)]],
      description: ['', Validators.maxLength(255)],
      confirmationMode: ['AUTOMATIC', Validators.required],
      requiresReference: [false],
      channels:    [['EXIT_SCREEN', 'STREET_KIOSK', 'MOBILE_APP', 'OPERATOR']],
      status:      [true]
    }, { validators: paymentChannelsValidator });
  }

  onSubmit(): void {
    if (this.form.invalid) { this.markTouched(this.form); return; }
    this.isSubmitting = true;
    this.errorMessage = null;

    this.paymentTypeService.create(this.form.value).subscribe({
      next: (res) => {
        this.isSubmitting = false;
        this.router.navigate(['/payment-types'], {
          state: { successMessage: `Tipo de pago "${res.name}" creado exitosamente` }
        });
      },
      error: (err) => {
        this.isSubmitting = false;
        this.errorMessage = err.error?.message || 'Error al crear el tipo de pago';
        window.scrollTo({ top: 0, behavior: 'smooth' });
      }
    });
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
    if (c?.errors?.['pattern'])    return 'De 2 a 20 caracteres: letras, dígitos o guion bajo';
    return '';
  }

  private markTouched(fg: FormGroup): void {
    Object.keys(fg.controls).forEach(k => fg.get(k)?.markAsTouched());
  }

  dismissError(): void { this.errorMessage = null; }
}