import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AbstractControl, FormBuilder, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ZoneService } from '../../../../core/services/parking-service/zone.service';
import { ParkingService } from '../../../../core/services/parking-service/parking.service';
import { Zone, ZoneRequest, ZoneUpdateRequest } from '../../../../core/models/parking-service/zone.model';
import { Parking } from '../../../../core/models/parking-service/parking.model';
import {
  LOCATION_STATUS_OPTIONS, MAX_CAMERAS_PER_ZONE, ZONE_CODE_PATTERN, apiError, cameraListValidator,
  parseCameras, suggestZoneCode,
} from '../../../../core/utils/parking.util';

/** Latitud y longitud van juntas o ninguna (igual que el backend) */
function coordinatesTogether(group: AbstractControl): ValidationErrors | null {
  const lat = group.get('latitude')?.value;
  const lng = group.get('longitude')?.value;
  const empty = (v: any) => v === null || v === undefined || v === '';
  return empty(lat) !== empty(lng) ? { coordinates: true } : null;
}

function numberOrUndefined(value: any): number | undefined {
  return value === null || value === undefined || value === '' ? undefined : Number(value);
}

/**
 * Crear (/zones/create?parkingId=) o editar (/zones/:id/edit) una zona.
 * El parqueo y el código no cambian después de crearla; el estado solo se edita.
 */
@Component({
  selector: 'app-zone-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './zone-form.component.html',
})
export class ZoneFormComponent implements OnInit {

  form!: FormGroup;
  zoneId: number | null = null;
  zone: Zone | null = null;
  parking: Parking | null = null;

  loadingData = true;
  saving = false;
  errorMessage = '';
  readonly statusOptions = LOCATION_STATUS_OPTIONS;
  readonly maxCameras = MAX_CAMERAS_PER_ZONE;

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private zoneService: ZoneService,
    private parkingService: ParkingService,
  ) {}

  get isEdit(): boolean {
    return this.zoneId !== null;
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    this.zoneId = id ? Number(id) : null;
    this.form = this.fb.group({
      name:        ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
      code:        ['', [Validators.required, Validators.pattern(ZONE_CODE_PATTERN)]],
      address:     ['', Validators.maxLength(255)],
      description: ['', Validators.maxLength(500)],
      latitude:    [null, [Validators.min(-90), Validators.max(90)]],
      longitude:   [null, [Validators.min(-180), Validators.max(180)]],
      cameras:     ['', cameraListValidator],
      status:      ['ACTIVE'],
    }, { validators: coordinatesTogether });

    if (this.isEdit) {
      this.form.get('code')?.disable();
      this.zoneService.getById(this.zoneId!).subscribe({
        next: zone => {
          this.zone = zone;
          this.form.patchValue({
            name: zone.name, code: zone.code, address: zone.address ?? '', description: zone.description ?? '',
            latitude: zone.latitude ?? null, longitude: zone.longitude ?? null,
            cameras: (zone.cameraIds ?? []).join(', '), status: zone.status,
          });
          this.loadParking(zone.parkingId);
        },
        error: err => { this.loadingData = false; this.errorMessage = apiError(err, 'No se pudo cargar la zona'); },
      });
    } else {
      const parkingId = Number(this.route.snapshot.queryParamMap.get('parkingId'));
      if (!parkingId) {
        this.loadingData = false;
        this.errorMessage = 'Crea las zonas desde el detalle de su parqueo.';
        return;
      }
      this.loadParking(parkingId);
    }
  }

  private loadParking(parkingId: number): void {
    this.parkingService.getById(parkingId).subscribe({
      next: p => { this.parking = p; this.loadingData = false; },
      error: err => { this.loadingData = false; this.errorMessage = apiError(err, 'No se pudo cargar el parqueo'); },
    });
  }

  /** Código en mayúsculas mientras se escribe */
  upperCode(): void {
    const ctrl = this.form.get('code');
    const value = (ctrl?.value ?? '').toUpperCase();
    if (value !== ctrl?.value) ctrl?.setValue(value, { emitEvent: false });
  }

  suggestCode(): void {
    if (this.isEdit || this.form.get('code')?.value) return;
    this.form.get('code')?.setValue(suggestZoneCode(this.form.get('name')?.value, 'Z1'));
  }

  isInvalid(field: string): boolean {
    const c = this.form.get(field);
    return !!(c && c.invalid && c.touched);
  }

  get cameraError(): string | null {
    return this.form.get('cameras')?.errors?.['cameras'] ?? null;
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const common = {
      name: v.name.trim(),
      address: v.address?.trim() || undefined,
      description: v.description?.trim() || undefined,
      latitude: numberOrUndefined(v.latitude),
      longitude: numberOrUndefined(v.longitude),
      cameraIds: parseCameras(v.cameras),
    };
    this.saving = true;
    this.errorMessage = '';
    const request$ = this.isEdit
      ? this.zoneService.update(this.zoneId!, { ...common, status: v.status } as ZoneUpdateRequest)
      : this.zoneService.create({ ...common, parkingId: this.parking!.id!, code: v.code.trim() } as ZoneRequest);
    request$.subscribe({
      next: zone => {
        this.saving = false;
        this.router.navigate(['/zones', zone.id], { state: {
          successMessage: this.isEdit ? `Zona "${zone.name}" actualizada`
            : `Zona "${zone.name}" creada. Agrega sus espacios.`,
        } });
      },
      error: err => { this.saving = false; this.errorMessage = apiError(err, 'No se pudo guardar la zona'); },
    });
  }

  cancel(): void {
    if (this.isEdit) this.router.navigate(['/zones', this.zoneId]);
    else if (this.parking) this.router.navigate(['/parkings', this.parking.id]);
    else this.router.navigate(['/parkings']);
  }
}
