// steps/step2-zones/step2-zones.component.ts
import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AbstractControl, FormArray, FormBuilder, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { concatMap, finalize, from, tap } from 'rxjs';
import { ZoneService } from '../../../../../core/services/parking-service/zone.service';
import { SPACE_BATCH_MAX } from '../../../../../core/services/parking-service/space.service';
import { Zone, ZoneRequest } from '../../../../../core/models/parking-service/zone.model';
import { Parking } from '../../../../../core/models/parking-service/parking.model';
import {
  CAMERA_ID_PATTERN, MAX_CAMERAS_PER_ZONE, ZONE_CODE_PATTERN, apiError,
} from '../../../../../core/utils/parking.util';

/** Zona creada con la cantidad de espacios que se generarán en el paso 3 */
export interface PlannedZone {
  zone: Zone;
  plannedSpaces: number;
}

/**
 * Paso 2: zonas del parqueo. Se crean una por una: si una falla, las anteriores quedan
 * guardadas y al reintentar solo se envían las que faltan.
 */
@Component({
  selector: 'app-step2-zones',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './step2-zones.component.html',
  styleUrl: './step2-zones.component.css',
})
export class Step2ZonesComponent implements OnInit {

  @Input() parking!: Parking;
  @Output() zonesCreated = new EventEmitter<PlannedZone[]>();

  form!: FormGroup;
  loading = false;
  errorMessage = '';
  readonly maxSpaces = SPACE_BATCH_MAX;

  /** Zona ya guardada por fila (índice del formulario) */
  private created = new Map<FormGroup, Zone>();

  constructor(private fb: FormBuilder, private zoneService: ZoneService) {}

  ngOnInit(): void {
    this.form = this.fb.group({ zones: this.fb.array([this.buildZoneGroup()]) }, { validators: uniqueCodes });
  }

  get zonesArray(): FormArray {
    return this.form.get('zones') as FormArray;
  }

  group(i: number): FormGroup {
    return this.zonesArray.at(i) as FormGroup;
  }

  private buildZoneGroup(): FormGroup {
    return this.fb.group({
      name:          ['', [Validators.required, Validators.maxLength(100)]],
      code:          ['', [Validators.required, Validators.pattern(ZONE_CODE_PATTERN)]],
      address:       ['', Validators.maxLength(200)],
      description:   ['', Validators.maxLength(500)],
      cameras:       ['', cameraListValidator],
      plannedSpaces: [10, [Validators.required, Validators.min(1), Validators.max(SPACE_BATCH_MAX)]],
    });
  }

  addZone(): void {
    this.zonesArray.push(this.buildZoneGroup());
  }

  removeZone(i: number): void {
    if (this.zonesArray.length > 1 && !this.isCreated(i)) {
      this.zonesArray.removeAt(i);
    }
  }

  isCreated(i: number): boolean {
    return this.created.has(this.group(i));
  }

  get createdCount(): number {
    return this.created.size;
  }

  // ========================= VALIDACIÓN =========================

  isInvalid(i: number, field: string): boolean {
    const ctrl = this.group(i).get(field);
    return !!(ctrl && ctrl.invalid && ctrl.touched) || (field === 'code' && this.isDuplicated(i));
  }

  isDuplicated(i: number): boolean {
    const code = (this.group(i).get('code')?.value ?? '').trim();
    return !!code && this.zonesArray.controls.filter(c => (c.get('code')?.value ?? '').trim() === code).length > 1;
  }

  getError(i: number, field: string): string {
    if (field === 'code' && this.isDuplicated(i)) return 'Este código ya se usa en otra zona del formulario.';
    const ctrl = this.group(i).get(field);
    if (!ctrl || !ctrl.errors || !ctrl.touched) return '';
    if (ctrl.errors['required'])  return 'Este campo es obligatorio.';
    if (ctrl.errors['maxlength']) return `Máximo ${ctrl.errors['maxlength'].requiredLength} caracteres.`;
    if (ctrl.errors['pattern'])   return 'Hasta 20 caracteres: mayúsculas, números, guion y guion bajo.';
    if (ctrl.errors['min'])       return 'Mínimo 1 espacio.';
    if (ctrl.errors['max'])       return `Máximo ${SPACE_BATCH_MAX} espacios por zona en el asistente (luego puedes agregar más).`;
    if (ctrl.errors['cameras'])   return ctrl.errors['cameras'];
    return 'Campo inválido.';
  }

  onCodeInput(event: Event, i: number): void {
    const input = event.target as HTMLInputElement;
    const upper = input.value.toUpperCase();
    this.group(i).get('code')?.setValue(upper, { emitEvent: false });
    input.value = upper;
  }

  /** Sugiere un código desde el nombre si aún está vacío: "Zona Norte" -> NORTE */
  suggestCode(i: number): void {
    const codeCtrl = this.group(i).get('code');
    if (codeCtrl?.value) return;
    const words = (this.group(i).get('name')?.value ?? '')
      .normalize('NFD').replace(/[̀-ͯ]/g, '')
      .toUpperCase().split(/[^A-Z0-9]+/).filter((w: string) => w && w !== 'ZONA');
    const code = (words.join('-') || `Z${i + 1}`).substring(0, 20).replace(/-+$/, '');
    codeCtrl?.setValue(code);
  }

  get totalSpaces(): number {
    return this.zonesArray.controls.reduce((sum, c) => sum + (Number(c.get('plannedSpaces')?.value) || 0), 0);
  }

  // ========================= GUARDAR =========================

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading = true;
    this.errorMessage = '';

    const pending = this.zonesArray.controls
      .map(c => c as FormGroup)
      .filter(g => !this.created.has(g));

    let current: FormGroup | null = null;
    from(pending).pipe(
      // Una tras otra y detenerse en la primera que falle: así se sabe cuáles se guardaron
      concatMap(g => {
        current = g;
        return this.zoneService.create(this.toRequest(g)).pipe(tap(zone => {
          this.created.set(g, zone);
          g.disable({ emitEvent: false });
          g.get('plannedSpaces')?.enable({ emitEvent: false });   // la cantidad sigue editable
        }));
      }),
      finalize(() => (this.loading = false)),
    ).subscribe({
      error: err => {
        const name = current?.get('name')?.value;
        const saved = this.created.size;
        this.errorMessage = apiError(err, `No se pudo crear la zona ${name}`) +
          (saved ? `. ${saved === 1 ? 'La zona anterior ya quedó guardada' : `Las ${saved} zonas anteriores ya quedaron guardadas`}.` : '');
      },
      complete: () => {
        this.zonesCreated.emit(this.zonesArray.controls.map(c => ({
          zone: this.created.get(c as FormGroup)!,
          plannedSpaces: Number(c.get('plannedSpaces')?.value) || 0,
        })));
      },
    });
  }

  private toRequest(g: FormGroup): ZoneRequest {
    const v = g.getRawValue();
    return {
      parkingId:   this.parking.id!,
      name:        v.name.trim(),
      code:        v.code.trim(),
      address:     v.address?.trim() || undefined,
      description: v.description?.trim() || undefined,
      cameraIds:   parseCameras(v.cameras),
    };
  }
}

/** "CAM-01, CAM-02" -> ["CAM-01", "CAM-02"] sin vacíos ni repetidos */
export function parseCameras(value: string | null | undefined): string[] {
  return [...new Set((value ?? '').split(',').map(c => c.trim()).filter(Boolean))];
}

function cameraListValidator(ctrl: AbstractControl): ValidationErrors | null {
  const cams = parseCameras(ctrl.value);
  if (cams.length > MAX_CAMERAS_PER_ZONE) {
    return { cameras: `Máximo ${MAX_CAMERAS_PER_ZONE} cámaras por zona.` };
  }
  const bad = cams.find(c => !CAMERA_ID_PATTERN.test(c));
  return bad ? { cameras: `Identificador de cámara inválido: ${bad}` } : null;
}

/** Los códigos no se repiten dentro del formulario */
function uniqueCodes(form: AbstractControl): ValidationErrors | null {
  const codes = ((form.get('zones') as FormArray)?.getRawValue() ?? [])
    .map((z: any) => (z.code ?? '').trim()).filter(Boolean);
  return new Set(codes).size !== codes.length ? { duplicatedCodes: true } : null;
}
