import { Component, DestroyRef, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { AbstractControl, FormBuilder, FormsModule, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { EMPTY, Observable, catchError, exhaustMap, filter, map, tap } from 'rxjs';
import { SPACE_BATCH_MAX, SpaceService } from '../../../../../core/services/parking-service/space.service';
import { AuthContextService } from '../../../../../core/services/auth-service/auth-context.service';
import { Space, SpaceRequest, SpaceUpdateRequest } from '../../../../../core/models/parking-service/space.model';
import { Zone } from '../../../../../core/models/parking-service/zone.model';
import { liveRefresh } from '../../../../../core/utils/live-refresh.util';
import { normalizeText } from '../../../../../core/utils/text.util';
import {
  SENSOR_ID_PATTERN, SPACE_CODE_PATTERN, SPACE_MANUAL_STATUS_OPTIONS, SPACE_STATUS_OPTIONS, SPACE_TYPE_OPTIONS,
  apiError, spaceCode, spaceStatus, spaceTypeLabel,
} from '../../../../../core/utils/parking.util';

type FormMode = 'create' | 'edit';
type Confirm = { kind: 'delete' | 'release' | 'restore'; space: Space };

/** Medidas opcionales entre 0.5 y 20 m (igual que el backend) */
const MEASURE = [Validators.min(0.5), Validators.max(20)];

/** El código más largo del lote no debe pasar de 20 caracteres */
function batchCodeLength(group: AbstractControl): ValidationErrors | null {
  const v = group.value;
  const last = (v.start ?? 1) + (v.count ?? 1) - 1;
  return spaceCode(v.prefix ?? '', last, v.digits ?? 3).length > 20 ? { codeLength: true } : null;
}

/**
 * Espacios de una zona (dentro de su detalle), en vivo cada 5 s.
 * OCUPADO solo lo pone la entrada de un vehículo o la detección; a mano se elige
 * libre, mantenimiento o fuera de servicio, y "Liberar" corrige un ocupado sin vehículo.
 */
@Component({
  selector: 'app-zone-spaces',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './zone-spaces.component.html',
  styleUrls: ['./zone-spaces.component.css'],
})
export class ZoneSpacesComponent implements OnInit {

  @Input() zone!: Zone;
  /** Avisa al detalle para que recalcule la capacidad */
  @Output() changed = new EventEmitter<void>();

  spaces: Space[] = [];
  loading = true;
  errorMessage = '';
  successMessage = '';
  busyId: number | null = null;

  // Filtros y paginación en el navegador (la zona trae todos sus espacios)
  search = '';
  statusFilter = '';
  typeFilter = '';
  page = 1;
  readonly pageSize = 20;

  showDeleted = false;
  deletedTotal = 0;

  // Formulario de un espacio
  formMode: FormMode | null = null;
  editingSpace: Space | null = null;
  spaceForm!: FormGroup;

  // Lote
  showBatch = false;
  batchForm!: FormGroup;

  saving = false;
  formError = '';
  confirm: Confirm | null = null;

  readonly statusOptions = SPACE_STATUS_OPTIONS;
  readonly manualStatusOptions = SPACE_MANUAL_STATUS_OPTIONS;
  readonly typeOptions = SPACE_TYPE_OPTIONS;
  readonly status = spaceStatus;
  readonly typeLabel = spaceTypeLabel;
  readonly batchMax = SPACE_BATCH_MAX;

  private destroyRef = inject(DestroyRef);

  constructor(
    private fb: FormBuilder,
    private spaceService: SpaceService,
    private authContext: AuthContextService,
  ) {}

  ngOnInit(): void {
    this.spaceForm = this.fb.group({
      code:              ['', [Validators.required, Validators.pattern(SPACE_CODE_PATTERN)]],
      type:              ['PERPENDICULAR', Validators.required],
      width:             [null, MEASURE],
      length:            [null, MEASURE],
      sensorId:          ['', Validators.pattern(SENSOR_ID_PATTERN)],
      hasCameraCoverage: [false],
      description:       ['', Validators.maxLength(500)],
      status:            ['AVAILABLE'],
    });
    this.batchForm = this.fb.group({
      prefix: [`${this.zone.code}-`, [Validators.maxLength(15), Validators.pattern(/^[A-Z0-9][A-Z0-9_-]*$/)]],
      count:  [10, [Validators.required, Validators.min(1), Validators.max(SPACE_BATCH_MAX)]],
      start:  [1, [Validators.required, Validators.min(0)]],
      digits: [3, [Validators.required, Validators.min(1), Validators.max(5)]],
      type:   ['PERPENDICULAR', Validators.required],
    }, { validators: batchCodeLength });

    this.load(false);
    liveRefresh(5000).pipe(
      filter(() => !this.saving),
      exhaustMap(() => this.request(true).pipe(catchError(() => EMPTY))),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(list => this.apply(list));
  }

  can(permission: string): boolean {
    return this.authContext.hasPermission(permission);
  }

  // ========================= CARGA =========================

  private request(silent: boolean): Observable<Space[]> {
    if (!this.showDeleted) return this.spaceService.getByZoneId(this.zone.id!, silent);
    // La papelera se pide paginada: se muestran los 100 primeros
    return this.spaceService.search(0, 100, { zoneId: this.zone.id, deleted: true }, silent).pipe(
      tap(r => (this.deletedTotal = r.totalElements)),
      map(r => r.content),
    );
  }

  private apply(list: Space[]): void {
    this.spaces = [...list].sort((a, b) => a.code.localeCompare(b.code, undefined, { numeric: true }));
    if (this.page > this.totalPages) this.page = Math.max(this.totalPages, 1);
  }

  load(silent: boolean): void {
    this.request(silent).subscribe({
      next: list => { this.loading = false; this.apply(list); },
      error: err => { this.loading = false; this.errorMessage = apiError(err, 'No se pudieron cargar los espacios'); },
    });
  }

  setShowDeleted(deleted: boolean): void {
    if (this.showDeleted === deleted) return;
    this.showDeleted = deleted;
    this.spaces = [];
    this.page = 1;
    this.loading = true;
    this.load(false);
  }

  /** Recarga la tabla y avisa al detalle (la capacidad cambia) */
  private refresh(message?: string): void {
    if (message) this.flash(message);
    this.load(true);
    this.changed.emit();
  }

  // ========================= FILTROS =========================

  get filtered(): Space[] {
    const term = normalizeText(this.search.trim());
    return this.spaces.filter(s =>
      (!term || normalizeText(`${s.code} ${s.sensorId ?? ''} ${s.description ?? ''}`).includes(term)) &&
      (!this.statusFilter || s.status === this.statusFilter) &&
      (!this.typeFilter || s.type === this.typeFilter));
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.filtered.length / this.pageSize));
  }

  get pageItems(): Space[] {
    const from = (this.page - 1) * this.pageSize;
    return this.filtered.slice(from, from + this.pageSize);
  }

  get pages(): number[] {
    const max = 5;
    let start = Math.max(1, this.page - Math.floor(max / 2));
    const end = Math.min(this.totalPages, start + max - 1);
    start = Math.max(1, end - max + 1);
    return Array.from({ length: end - start + 1 }, (_, i) => start + i);
  }

  goToPage(p: number): void {
    if (p >= 1 && p <= this.totalPages) this.page = p;
  }

  onFilterChange(): void {
    this.page = 1;
  }

  clearFilters(): void {
    this.search = '';
    this.statusFilter = '';
    this.typeFilter = '';
    this.page = 1;
  }

  /** Conteo por estado para el resumen sobre la tabla */
  count(status: string): number {
    return this.spaces.filter(s => s.status === status).length;
  }

  // ========================= CREAR Y EDITAR UNO =========================

  openCreate(): void {
    this.formError = '';
    this.formMode = 'create';
    this.editingSpace = null;
    this.spaceForm.reset({ code: '', type: 'PERPENDICULAR', width: null, length: null, sensorId: '',
      hasCameraCoverage: false, description: '', status: 'AVAILABLE' });
    this.spaceForm.get('code')?.enable();
    this.spaceForm.get('status')?.disable();
  }

  openEdit(space: Space): void {
    this.formError = '';
    this.formMode = 'edit';
    this.editingSpace = space;
    this.spaceForm.reset({ code: space.code, type: space.type, width: space.width ?? null, length: space.length ?? null,
      sensorId: space.sensorId ?? '', hasCameraCoverage: !!space.hasCameraCoverage,
      description: space.description ?? '', status: space.status });
    this.spaceForm.get('code')?.disable();
    // Un espacio ocupado conserva su estado: se libera con "Liberar" o con la salida del vehículo
    space.status === 'OCCUPIED' ? this.spaceForm.get('status')?.disable() : this.spaceForm.get('status')?.enable();
  }

  closeForm(): void {
    this.formMode = null;
    this.editingSpace = null;
  }

  upperCode(form: FormGroup, field: string): void {
    const ctrl = form.get(field);
    const value = (ctrl?.value ?? '').toUpperCase();
    if (value !== ctrl?.value) ctrl?.setValue(value, { emitEvent: false });
  }

  isInvalid(form: FormGroup, field: string): boolean {
    const c = form.get(field);
    return !!(c && c.invalid && c.touched);
  }

  saveSpace(): void {
    if (this.spaceForm.invalid) {
      this.spaceForm.markAllAsTouched();
      return;
    }
    const v = this.spaceForm.getRawValue();
    const common = {
      type: v.type,
      width: v.width === null || v.width === '' ? undefined : Number(v.width),
      length: v.length === null || v.length === '' ? undefined : Number(v.length),
      sensorId: v.sensorId?.trim() || undefined,
      hasCameraCoverage: !!v.hasCameraCoverage,
      description: v.description?.trim() || undefined,
    };
    const edit = this.editingSpace;
    const request$ = edit
      ? this.spaceService.update(edit.id!, {
          ...common, status: edit.status === 'OCCUPIED' ? undefined : v.status,
        } as SpaceUpdateRequest)
      : this.spaceService.create({ ...common, zoneId: this.zone.id!, code: v.code.trim() } as SpaceRequest);
    this.saving = true;
    this.formError = '';
    request$.subscribe({
      next: s => {
        this.saving = false;
        this.closeForm();
        this.refresh(edit ? `Espacio ${s.code} actualizado` : `Espacio ${s.code} creado`);
      },
      error: err => { this.saving = false; this.formError = apiError(err, 'No se pudo guardar el espacio'); },
    });
  }

  // ========================= LOTE =========================

  openBatch(): void {
    this.formError = '';
    const next = this.nextNumber();
    this.batchForm.reset({ prefix: `${this.zone.code}-`, count: 10, start: next, digits: 3, type: 'PERPENDICULAR' });
    this.showBatch = true;
  }

  /** Sigue la numeración: el mayor número con el mismo prefijo, más uno */
  private nextNumber(prefix = `${this.zone.code}-`): number {
    const numbers = this.spaces
      .filter(s => s.code.startsWith(prefix))
      .map(s => Number(s.code.substring(prefix.length)))
      .filter(n => Number.isInteger(n));
    return numbers.length ? Math.max(...numbers) + 1 : 1;
  }

  get batchCodes(): string[] {
    const v = this.batchForm.getRawValue();
    const count = Math.min(Math.max(Number(v.count) || 0, 0), SPACE_BATCH_MAX);
    const start = Number(v.start) || 0;
    return Array.from({ length: count }, (_, i) => spaceCode(v.prefix ?? '', start + i, Number(v.digits) || 3));
  }

  /** Códigos del lote que ya existen en la zona (el backend rechazaría todo el lote) */
  get batchCollisions(): string[] {
    const existing = new Set(this.spaces.map(s => s.code));
    return this.batchCodes.filter(c => existing.has(c));
  }

  saveBatch(): void {
    if (this.batchForm.invalid || this.batchCollisions.length) {
      this.batchForm.markAllAsTouched();
      return;
    }
    const type = this.batchForm.getRawValue().type;
    const requests: SpaceRequest[] = this.batchCodes.map(code => ({ zoneId: this.zone.id!, code, type }));
    this.saving = true;
    this.formError = '';
    this.spaceService.createBatch(requests).subscribe({
      next: created => {
        this.saving = false;
        this.showBatch = false;
        this.refresh(`${created.length} espacios creados`);
      },
      error: err => { this.saving = false; this.formError = apiError(err, 'No se pudieron crear los espacios'); },
    });
  }

  // ========================= ESTADO =========================

  setStatus(space: Space, target: 'AVAILABLE' | 'MAINTENANCE' | 'OUT_OF_SERVICE'): void {
    const request$ = target === 'MAINTENANCE' ? this.spaceService.setInMaintenance(space.id!)
      : target === 'OUT_OF_SERVICE' ? this.spaceService.setOutOfService(space.id!)
      : this.spaceService.toggleStatus(space.id!);   // desde mantenimiento o fuera de servicio pasa a libre
    this.run(space, request$, s => `Espacio ${s.code}: ${this.status(s.status).label.toLowerCase()}`);
  }

  // ========================= CONFIRMACIONES =========================

  ask(kind: Confirm['kind'], space: Space): void {
    this.confirm = { kind, space };
  }

  confirmAction(): void {
    const c = this.confirm;
    if (!c) return;
    this.confirm = null;
    const id = c.space.id!;
    if (c.kind === 'delete') {
      this.run(c.space, this.spaceService.delete(id), () => `Espacio ${c.space.code} eliminado`);
    } else if (c.kind === 'release') {
      this.run(c.space, this.spaceService.release(id), s => `Espacio ${s.code} liberado`);
    } else {
      this.run(c.space, this.spaceService.restore(id), s => `Espacio ${s.code} restaurado`);
    }
  }

  private run<T>(space: Space, request$: Observable<T>, message: (result: T) => string): void {
    this.busyId = space.id!;
    this.errorMessage = '';
    request$.subscribe({
      next: r => { this.busyId = null; this.refresh(message(r)); },
      error: err => { this.busyId = null; this.errorMessage = apiError(err, `No se pudo cambiar el espacio ${space.code}`); },
    });
  }

  private flash(message: string): void {
    this.successMessage = message;
    setTimeout(() => (this.successMessage = ''), 4000);
  }
}
