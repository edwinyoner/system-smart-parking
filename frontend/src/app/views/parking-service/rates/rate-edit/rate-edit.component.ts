// src/app/views/parking-service/rates/rate-edit/rate-edit.component.ts
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

// Services
import { RateService } from '../../../../core/services/parking-service/rate.service';

// Models
import { Rate } from '../../../../core/models/parking-service/rate.model';

// Components
import { RateBillingComponent } from '../rate-billing/rate-billing.component';
import { rateBillingControls, toleranceValidator } from '../../../../core/utils/rate.util';
import { AlertMessageComponent } from '../../../../shared/components/alert-message/alert-message.component';

@Component({
  selector: 'app-rate-edit',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    AlertMessageComponent,
    RateBillingComponent
  ],
  templateUrl: './rate-edit.component.html',
  styleUrls: ['./rate-edit.component.css']
})
export class RateEditComponent implements OnInit {

  rateId!: number;
  rateForm!: FormGroup;
  isLoading = true;
  isSubmitting = false;
  errorMessage: string | null = null;
  currentRate: Rate | null = null;


  constructor(
    private fb: FormBuilder,
    private rateService: RateService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.rateId = Number(this.route.snapshot.paramMap.get('id'));
    this.initForm();
    this.loadRate();
  }

  private initForm(): void {
    // La moneda no se elige: el sistema cobra siempre en soles
    this.rateForm = this.fb.group({
      name: ['', [Validators.required, Validators.maxLength(100)]],
      description: ['', Validators.maxLength(500)],
      ...rateBillingControls(this.fb),
      status: [true]
    }, { validators: toleranceValidator });
  }

  private loadRate(): void {
    this.isLoading = true;
    
    this.rateService.getById(this.rateId).subscribe({
      next: (rate: Rate) => {
        this.currentRate = rate;
        this.rateForm.patchValue({
          name: rate.name,
          description: rate.description,
          amount: rate.amount,
          billingUnitMinutes: rate.billingUnitMinutes,
          minimumMinutes: rate.minimumMinutes,
          graceMinutes: rate.graceMinutes,
          toleranceMinutes: rate.toleranceMinutes,
          status: rate.status
        });
        this.isLoading = false;
      },
      error: (error) => {
        this.isLoading = false;
        this.errorMessage = 'Error al cargar la tarifa';
        console.error(error);
      }
    });
  }

  onSubmit(): void {
    if (this.rateForm.invalid) {
      this.markFormGroupTouched(this.rateForm);
      return;
    }

    this.isSubmitting = true;
    this.errorMessage = null;

    this.rateService.update(this.rateId, this.rateForm.value).subscribe({
      next: (response) => {
        this.isSubmitting = false;
        this.router.navigate(['/rates'], {
          state: { successMessage: `Tarifa "${response.name}" actualizada exitosamente` }
        });
      },
      error: (error) => {
        this.isSubmitting = false;
        this.errorMessage = error.error?.message || 'Error al actualizar la tarifa';
        window.scrollTo({ top: 0, behavior: 'smooth' });
      }
    });
  }

  /** Desactivar una tarifa en uso: avisa qué parqueos la usan */
  get deactivationWarning(): string | null {
    const parkings = this.currentRate?.usedByParkings ?? [];
    const willDeactivate = this.currentRate?.status && !this.rateForm.get('status')?.value;
    return willDeactivate && parkings.length > 0 ? parkings.join(', ') : null;
  }

  /** Cambiar el precio afecta también a los vehículos que ya están dentro */
  get priceChangeWarning(): string | null {
    const parkings = this.currentRate?.usedByParkings ?? [];
    if (!this.currentRate || parkings.length === 0) return null;
    const v = this.rateForm.getRawValue();
    const changed = Number(v.amount) !== Number(this.currentRate.amount)
      || Number(v.billingUnitMinutes) !== this.currentRate.billingUnitMinutes
      || Number(v.minimumMinutes) !== this.currentRate.minimumMinutes
      || Number(v.graceMinutes) !== this.currentRate.graceMinutes
      || Number(v.toleranceMinutes) !== this.currentRate.toleranceMinutes;
    return changed ? parkings.join(', ') : null;
  }

  toggleStatus(): void {
    const currentStatus = this.rateForm.get('status')?.value;
    this.rateForm.patchValue({ status: !currentStatus });
  }

  cancel(): void {
    this.router.navigate(['/rates']);
  }

  // Utilidades
  isFieldInvalid(fieldName: string): boolean {
    const field = this.rateForm.get(fieldName);
    return !!(field && field.invalid && (field.dirty || field.touched));
  }

  getFieldError(fieldName: string): string {
    const field = this.rateForm.get(fieldName);
    if (field?.errors) {
      if (field.errors['required']) return 'Este campo es obligatorio';
      if (field.errors['maxlength']) return `Máximo ${field.errors['maxlength'].requiredLength} caracteres`;
      if (field.errors['min']) return `Debe ser al menos ${field.errors['min'].min}`;
      if (field.errors['decimals']) return 'Como máximo 2 decimales';
    }
    return '';
  }


  private markFormGroupTouched(formGroup: FormGroup): void {
    Object.keys(formGroup.controls).forEach(key => {
      const control = formGroup.get(key);
      control?.markAsTouched();
      if (control instanceof FormGroup) {
        this.markFormGroupTouched(control);
      }
    });
  }

  dismissError(): void {
    this.errorMessage = null;
  }
}