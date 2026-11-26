// steps/step1-info/step1-info.component.ts
import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ParkingService } from '../../../../../core/services/parking-service/parking.service';
import { UserService } from '../../../../../core/services/auth-service/user.service';
import { Parking, ParkingRequest } from '../../../../../core/models/parking-service/parking.model';
import { apiError } from '../../../../../core/utils/parking.util';
import {
  ManagerOption, buildParkingForm, fieldError, loadManagerOptions, numberOrUndefined,
} from '../../shared/parking-form.util';

/**
 * Paso 1: datos del parqueo. Al guardar, el parqueo ya existe (sin zonas ni horario).
 */
@Component({
  selector: 'app-step1-info',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './step1-info.component.html',
  styleUrl: './step1-info.component.css',
})
export class Step1InfoComponent implements OnInit {

  @Output() parkingCreated = new EventEmitter<Parking>();

  form!: FormGroup;
  loading = false;
  errorMessage = '';
  /** null = sin permiso users.read para elegir responsable */
  managers: ManagerOption[] | null = [];

  constructor(
    private fb: FormBuilder,
    private parkingService: ParkingService,
    private userService: UserService,
  ) {}

  ngOnInit(): void {
    this.form = buildParkingForm(this.fb, true);
    loadManagerOptions(this.userService).subscribe(list => {
      this.managers = list;
      if (list === null) {
        this.form.get('managerId')?.disable();
      }
    });
  }

  isInvalid(field: string): boolean {
    const ctrl = this.form.get(field);
    return !!(ctrl && ctrl.invalid && ctrl.touched);
  }

  getError(field: string): string {
    return fieldError(this.form, field);
  }

  get coordinatesError(): boolean {
    return !!this.form.errors?.['coordinates'] &&
      (!!this.form.get('latitude')?.touched || !!this.form.get('longitude')?.touched);
  }

  /** El código se escribe en mayúsculas, como lo guarda el backend */
  onCodeInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const upper = input.value.toUpperCase();
    this.form.get('code')?.setValue(upper, { emitEvent: false });
    input.value = upper;
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading = true;
    this.errorMessage = '';

    const v = this.form.getRawValue();
    const request: ParkingRequest = {
      name:        v.name.trim(),
      code:        v.code.trim(),
      address:     v.address.trim(),
      description: v.description?.trim() || undefined,
      latitude:    numberOrUndefined(v.latitude),
      longitude:   numberOrUndefined(v.longitude),
      managerId:   v.managerId ? Number(v.managerId) : undefined,
    };

    this.parkingService.create(request).subscribe({
      next: parking => {
        this.loading = false;
        this.parkingCreated.emit(parking);
      },
      error: err => {
        this.loading = false;
        this.errorMessage = apiError(err, 'No se pudo crear el parqueo. Intenta nuevamente.');
      },
    });
  }
}
