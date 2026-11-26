// steps/step3-spaces/step3-spaces.component.ts
import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AbstractControl, FormArray, FormBuilder, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { concatMap, finalize, from, tap } from 'rxjs';
import { SPACE_BATCH_MAX, SpaceService } from '../../../../../core/services/parking-service/space.service';
import { SpaceRequest } from '../../../../../core/models/parking-service/space.model';
import { SPACE_CODE_PATTERN, SPACE_TYPE_OPTIONS, apiError, spaceCode } from '../../../../../core/utils/parking.util';
import { PlannedZone } from '../step2-zones/step2-zones.component';

/**
 * Paso 3: genera los espacios de cada zona con códigos correlativos (NORTE-001, NORTE-002...).
 * Cada zona se envía como un lote todo o nada; si una falla, las anteriores quedan guardadas
 * y al reintentar solo se envían las que faltan.
 */
@Component({
  selector: 'app-step3-spaces',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './step3-spaces.component.html',
  styleUrl: './step3-spaces.component.css',
})
export class Step3SpacesComponent implements OnInit {

  @Input() zones: PlannedZone[] = [];
  @Output() spacesCreated = new EventEmitter<void>();

  form!: FormGroup;
  loading = false;
  errorMessage = '';
  readonly spaceTypes = SPACE_TYPE_OPTIONS;
  readonly maxSpaces = SPACE_BATCH_MAX;

  /** Zonas cuyos espacios ya se guardaron */
  private done = new Set<number>();

  constructor(private fb: FormBuilder, private spaceService: SpaceService) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      zoneConfigs: this.fb.array(this.zones.map(pz => this.fb.group({
        prefix:   [pz.zone.code, [Validators.required, Validators.maxLength(15), Validators.pattern(SPACE_CODE_PATTERN)]],
        count:    [Math.max(1, pz.plannedSpaces || 1), [Validators.required, Validators.min(1), Validators.max(SPACE_BATCH_MAX)]],
        start:    [1, [Validators.required, Validators.min(1), Validators.max(99999)]],
        type:     ['PERPENDICULAR', Validators.required],
      }, { validators: codeLengthValidator }))),
    });
  }

  get zoneConfigs(): FormArray {
    return this.form.get('zoneConfigs') as FormArray;
  }

  cfg(i: number): FormGroup {
    return this.zoneConfigs.at(i) as FormGroup;
  }

  isDone(i: number): boolean {
    return this.done.has(i);
  }

  // ========================= VISTA PREVIA =========================

  /** Primeros y último código que se generarán */
  preview(i: number): string {
    const codes = this.codesFor(i);
    if (codes.length === 0) return '';
    if (codes.length <= 4) return codes.join(', ');
    return `${codes.slice(0, 3).join(', ')} ... ${codes[codes.length - 1]}`;
  }

  private codesFor(i: number): string[] {
    const v = this.cfg(i).getRawValue();
    const count = Math.min(Number(v.count) || 0, SPACE_BATCH_MAX);
    const start = Number(v.start) || 1;
    const prefix = (v.prefix ?? '').trim();
    if (!prefix || count < 1) return [];
    const digits = Math.max(3, String(start + count - 1).length);
    return Array.from({ length: count }, (_, k) => spaceCode(prefix, start + k, digits));
  }

  get totalSpacesToCreate(): number {
    return this.zoneConfigs.controls.reduce((sum, c) => sum + (Number(c.get('count')?.value) || 0), 0);
  }

  // ========================= VALIDACIÓN =========================

  isInvalid(i: number, field: string): boolean {
    const ctrl = this.cfg(i).get(field);
    return !!(ctrl && ctrl.invalid && ctrl.touched) || (field === 'prefix' && !!this.cfg(i).errors?.['codeTooLong']);
  }

  getError(i: number, field: string): string {
    if (field === 'prefix' && this.cfg(i).errors?.['codeTooLong']) {
      return 'Con este prefijo los códigos pasarían de 20 caracteres.';
    }
    const ctrl = this.cfg(i).get(field);
    if (!ctrl || !ctrl.errors || !ctrl.touched) return '';
    if (ctrl.errors['required'])  return 'Obligatorio.';
    if (ctrl.errors['pattern'])   return 'Mayúsculas, números, guion y guion bajo.';
    if (ctrl.errors['maxlength']) return `Máximo ${ctrl.errors['maxlength'].requiredLength} caracteres.`;
    if (ctrl.errors['min'])       return 'Mínimo 1.';
    if (ctrl.errors['max'])       return field === 'count' ? `Máximo ${SPACE_BATCH_MAX} por zona.` : 'Valor demasiado alto.';
    return 'Inválido.';
  }

  onPrefixInput(event: Event, i: number): void {
    const input = event.target as HTMLInputElement;
    const upper = input.value.toUpperCase();
    this.cfg(i).get('prefix')?.setValue(upper);
    input.value = upper;
  }

  // ========================= GUARDAR =========================

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading = true;
    this.errorMessage = '';
    const pending = this.zones.map((_, i) => i).filter(i => !this.done.has(i));
    let current = -1;

    from(pending).pipe(
      concatMap(i => {
        current = i;
        return this.spaceService.createBatch(this.buildRequests(i)).pipe(tap(() => {
          this.done.add(i);
          this.cfg(i).disable({ emitEvent: false });
        }));
      }),
      finalize(() => (this.loading = false)),
    ).subscribe({
      error: err => {
        const zone = this.zones[current]?.zone.name;
        this.errorMessage = apiError(err, `No se pudieron crear los espacios de ${zone}`) +
          `. En ${zone} no se creó ninguno: corrige y vuelve a intentar.` +
          (this.done.size ? ` Las zonas marcadas como listas ya tienen sus espacios.` : '');
      },
      complete: () => this.spacesCreated.emit(),
    });
  }

  private buildRequests(i: number): SpaceRequest[] {
    const type = this.cfg(i).getRawValue().type;
    const zoneId = this.zones[i].zone.id!;
    return this.codesFor(i).map(code => ({ zoneId, code, type }));
  }
}

/** prefijo + "-" + dígitos no puede pasar de 20 caracteres (límite del código) */
function codeLengthValidator(group: AbstractControl): ValidationErrors | null {
  const prefix = (group.get('prefix')?.value ?? '').trim();
  const last = (Number(group.get('start')?.value) || 1) + (Number(group.get('count')?.value) || 1) - 1;
  const digits = Math.max(3, String(last).length);
  return prefix && prefix.length + 1 + digits > 20 ? { codeTooLong: true } : null;
}
