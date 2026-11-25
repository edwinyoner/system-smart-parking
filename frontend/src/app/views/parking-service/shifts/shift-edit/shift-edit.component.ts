// src/app/views/parking-service/shifts/shift-edit/shift-edit.component.ts
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

// Services
import { crossesMidnight, formatShiftDuration, shiftDurationHours, shiftTimeValidator } from '../../../../core/utils/shift.util';
import { ShiftService } from '../../../../core/services/parking-service/shift.service';

// Models
import { Shift } from '../../../../core/models/parking-service/shift.model';

// Components
import { AlertMessageComponent } from '../../../../shared/components/alert-message/alert-message.component';

@Component({
  selector: 'app-shift-edit',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    AlertMessageComponent
  ],
  templateUrl: './shift-edit.component.html',
  styleUrls: ['./shift-edit.component.css']
})
export class ShiftEditComponent implements OnInit {

  shiftId!: number;
  shiftForm!: FormGroup;
  isLoading = true;
  isSubmitting = false;
  errorMessage: string | null = null;
  currentShift: Shift | null = null;

  constructor(
    private fb: FormBuilder,
    private shiftService: ShiftService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.shiftId = Number(this.route.snapshot.paramMap.get('id'));
    this.initForm();
    this.loadShift();
  }

  private initForm(): void {
    this.shiftForm = this.fb.group({
      name: ['', [Validators.required, Validators.maxLength(100)]],
      code: [{ value: '', disabled: true }],   // No se cambia: lo usan las tarifas
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

  /** Desactivar un turno en uso: esos parqueos dejan de operar en este horario */
  get deactivationWarning(): string | null {
    const parkings = this.currentShift?.usedByParkings ?? [];
    const willDeactivate = this.currentShift?.status && !this.shiftForm.get('status')?.value;
    return willDeactivate && parkings.length > 0 ? parkings.join(', ') : null;
  }

  private loadShift(): void {
    this.isLoading = true;
    
    this.shiftService.getById(this.shiftId).subscribe({
      next: (shift: Shift) => {
        this.currentShift = shift;
        this.shiftForm.patchValue({
          name: shift.name,
          code: shift.code,
          description: shift.description,
          startTime: shift.startTime,
          endTime: shift.endTime,
          status: shift.status
        });
        this.isLoading = false;
      },
      error: (error) => {
        this.isLoading = false;
        this.errorMessage = 'Error al cargar el turno';
        console.error(error);
      }
    });
  }

  onSubmit(): void {
    if (this.shiftForm.invalid) {
      this.markFormGroupTouched(this.shiftForm);
      return;
    }

    this.isSubmitting = true;
    this.errorMessage = null;

    this.shiftService.update(this.shiftId, this.shiftForm.value).subscribe({
      next: (response) => {
        this.isSubmitting = false;
        this.router.navigate(['/shifts'], {
          state: { successMessage: `Turno "${response.name}" actualizado exitosamente` }
        });
      },
      error: (error) => {
        this.isSubmitting = false;
        this.errorMessage = error.error?.message || 'Error al actualizar el turno';
        window.scrollTo({ top: 0, behavior: 'smooth' });
      }
    });
  }

  toggleStatus(): void {
    const currentStatus = this.shiftForm.get('status')?.value;
    this.shiftForm.patchValue({ status: !currentStatus });
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