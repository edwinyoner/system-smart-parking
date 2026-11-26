import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ParkingService } from '../../../../core/services/parking-service/parking.service';
import { UserService } from '../../../../core/services/auth-service/user.service';
import { Parking, ParkingStatus, ParkingUpdateRequest } from '../../../../core/models/parking-service/parking.model';
import { LOCATION_STATUS_OPTIONS, apiError } from '../../../../core/utils/parking.util';
import {
  ManagerOption, buildParkingForm, fieldError, loadManagerOptions, numberOrUndefined,
} from '../shared/parking-form.util';

/**
 * Editar los datos de un parqueo. El código no cambia. El backend reemplaza todo el
 * registro, así que se envía también el responsable actual (si no, se quitaría).
 */
@Component({
  selector: 'app-parking-edit',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './parking-edit.component.html',
  styleUrl: './parking-edit.component.css',
})
export class ParkingEditComponent implements OnInit {

  form!: FormGroup;
  parking: Parking | null = null;
  parkingId!: number;

  loading = false;
  loadingData = true;
  errorMessage = '';
  managers: ManagerOption[] | null = [];
  readonly statusOptions = LOCATION_STATUS_OPTIONS;

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private parkingService: ParkingService,
    private userService: UserService,
  ) {}

  ngOnInit(): void {
    this.form = buildParkingForm(this.fb, false);
    this.form.addControl('status', this.fb.control<ParkingStatus>('ACTIVE'));
    this.parkingId = Number(this.route.snapshot.paramMap.get('id'));
    this.loadParking();
    loadManagerOptions(this.userService).subscribe(list => {
      this.managers = list;
      if (list === null) {
        // Sin permiso para ver usuarios: se conserva el responsable actual sin poder cambiarlo
        this.form.get('managerId')?.disable();
      }
    });
  }

  loadParking(): void {
    this.loadingData = true;
    this.errorMessage = '';
    this.parkingService.getById(this.parkingId).subscribe({
      next: parking => {
        this.parking = parking;
        this.form.patchValue({
          name: parking.name,
          address: parking.address,
          description: parking.description ?? '',
          latitude: parking.latitude ?? null,
          longitude: parking.longitude ?? null,
          managerId: parking.managerId ?? null,
          status: parking.status,
          requireDriverDocument: !!parking.requireDriverDocument,
        });
        this.loadingData = false;
      },
      error: err => {
        this.errorMessage = apiError(err, 'No se pudo cargar el parqueo');
        this.loadingData = false;
      },
    });
  }

  /** El responsable actual puede no estar en la lista (desactivado): se muestra igual */
  get currentManagerMissing(): boolean {
    const id = this.parking?.managerId;
    return !!id && !(this.managers ?? []).some(m => m.id === id);
  }

  isInvalid(field: string): boolean {
    const ctrl = this.form.get(field);
    return !!(ctrl && ctrl.invalid && ctrl.touched);
  }

  getError(field: string): string {
    return fieldError(this.form, field);
  }

  get coordinatesError(): boolean {
    return !!this.form.errors?.['coordinates'];
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading = true;
    this.errorMessage = '';
    const v = this.form.getRawValue();
    const request: ParkingUpdateRequest = {
      name: v.name.trim(),
      address: v.address.trim(),
      description: v.description?.trim() || undefined,
      latitude: numberOrUndefined(v.latitude),
      longitude: numberOrUndefined(v.longitude),
      managerId: v.managerId ? Number(v.managerId) : null,
      status: v.status,
      requireDriverDocument: !!v.requireDriverDocument,
    };
    this.parkingService.update(this.parkingId, request).subscribe({
      next: parking => {
        this.loading = false;
        this.router.navigate(['/parkings', parking.id],
          { state: { successMessage: `Parqueo "${parking.name}" actualizado` } });
      },
      error: err => {
        this.loading = false;
        this.errorMessage = apiError(err, 'No se pudo actualizar el parqueo');
      },
    });
  }

  cancel(): void {
    this.router.navigate(['/parkings', this.parkingId]);
  }
}
