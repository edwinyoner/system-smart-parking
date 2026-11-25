// src/app/views/parking-service/shifts/shift-create/shift-create.component.ts
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

// Services
import { crossesMidnight, formatShiftDuration, shiftDurationHours, shiftTimeValidator } from '../../../../core/utils/shift.util';
import { ShiftService } from '../../../../core/services/parking-service/shift.service';

// Components
import { AlertMessageComponent } from '../../../../shared/components/alert-message/alert-message.component';

@Component({
  selector: 'app-shift-create',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    AlertMessageComponent
  ],
  templateUrl: './shift-create.component.html',
  styleUrls: ['./shift-create.component.css']
})
export class ShiftCreateComponent implements OnInit {

  shiftForm!: FormGroup;
  isSubmitting = false;
  errorMessage: string | null = null;

  constructor(
    private fb: FormBuilder,
    private shiftService: ShiftService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.initForm();
  }

  private initForm(): void {
    this.shiftForm = this.fb.group({
      name: ['', [Validators.required, Validators.maxLength(100)]],
      code: ['', [Validators.required, Validators.maxLength(50), Validators.pattern(/^[A-Za-z0-9_]+$/)]],
      description: [''],
      startTime: ['', Validators.required],
      endTime: ['', Validators.required],
      status: [true]
    }, {
      validators: shiftTimeValidator
    });
  }

  /** Fin menor que inicio: el turno termina al día siguiente (válido) */
  get crossesMidnight(): boolean {
    return crossesMidnight(this.shiftForm.get('startTime')?.value, this.shiftForm.get('endTime')?.value);
  }

  get durationLabel(): string {
    const hours = shiftDurationHours(this.shiftForm.get('startTime')?.value, this.shiftForm.get('endTime')?.value);
    return hours == null ? '' : formatShiftDuration(hours);
  }

  onSubmit(): void {
    if (this.shiftForm.invalid) {
      this.markFormGroupTouched(this.shiftForm);
      return;
    }

    this.isSubmitting = true;
    this.errorMessage = null;

    // El código se guarda en mayúsculas (el backend también lo normaliza)
    const value = this.shiftForm.getRawValue();
    this.shiftService.create({ ...value, code: value.code.trim().toUpperCase() }).subscribe({
      next: (response) => {
        this.isSubmitting = false;
        this.router.navigate(['/shifts'], {
          state: { successMessage: `Turno "${response.name}" creado exitosamente` }
        });
      },
      error: (error) => {
        this.isSubmitting = false;
        this.errorMessage = error.error?.message || 'Error al crear el turno';
        window.scrollTo({ top: 0, behavior: 'smooth' });
      }
    });
  }

  cancel(): void {
    this.router.navigate(['/shifts']);
  }

  // Utilidades
  isFieldInvalid(fieldName: string): boolean {
    const field = this.shiftForm.get(fieldName);
    return !!(field && field.invalid && (field.dirty || field.touched));
  }

  getFieldError(fieldName: string): string {
    const field = this.shiftForm.get(fieldName);
    if (field?.errors) {
      if (field.errors['required']) return 'Este campo es obligatorio';
      if (field.errors['maxlength']) return `Máximo ${field.errors['maxlength'].requiredLength} caracteres`;
      if (field.errors['pattern']) return 'Solo letras, números y guiones bajos (sin espacios)';
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