// src/app/views/parking-service/infractions/infraction-form/infraction-form.component.ts
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, forkJoin, of } from 'rxjs';
import {
  AlertComponent,
  ButtonDirective,
  CardBodyComponent,
  CardComponent,
  CardHeaderComponent,
  SpinnerComponent
} from '@coreui/angular';

import { InfractionService } from '../../../../core/services/parking-service/infraction.service';
import { ParkingService } from '../../../../core/services/parking-service/parking.service';
import { ZoneService } from '../../../../core/services/parking-service/zone.service';
import { SpaceService } from '../../../../core/services/parking-service/space.service';
import { TransactionService } from '../../../../core/services/parking-service/transaction.service';
import { OperatorAssignmentService } from '../../../../core/services/parking-service/operator-assignment.service';
import { PermissionService } from '../../../../core/services/auth-service/permission.service';
import {
  Infraction,
  InfractionSeverity,
  InfractionType
} from '../../../../core/models/parking-service/infraction.model';
import { Parking } from '../../../../core/models/parking-service/parking.model';
import { Zone } from '../../../../core/models/parking-service/zone.model';
import { Space } from '../../../../core/models/parking-service/space.model';
import { TransactionDetailResponse } from '../../../../core/models/parking-service/transaction.model';
import { OperatorAssignment } from '../../../../core/models/parking-service/operator-assignment.model';
import { apiError, localDate } from '../../../../core/utils/parking.util';
import { normalizePlate, plateValidator } from '../../../../core/utils/plate.util';
import {
  INFRACTION_EVIDENCE_MAX,
  INFRACTION_FINE_MAX,
  INFRACTION_SEVERITIES,
  INFRACTION_TYPES,
  isEvidenceUrl,
  localDateTimeInput
} from '../../../../core/utils/infraction.util';

const ANY_PARKING = 'transactions.any-parking';
const DEFAULT_DUE_DAYS = 7;

/** Una URL de evidencia por línea, http o https, hasta 5 */
function evidenceValidator(control: AbstractControl): ValidationErrors | null {
  const lines = splitLines(control.value);
  if (lines.length > INFRACTION_EVIDENCE_MAX) return { evidenceMax: true };
  return lines.every(isEvidenceUrl) ? null : { evidenceUrl: true };
}

function splitLines(value: unknown): string[] {
  return String(value ?? '').split(/\r?\n/).map(s => s.trim()).filter(Boolean);
}

/**
 * Registrar o editar una infracción.
 *
 * Registrar (/infractions/create): solo en los parqueos y zonas donde el usuario está de turno
 * (o todos con transactions.any-parking); el backend lo vuelve a comprobar (403).
 * Con ?transactionId= se toma la estadía: parqueo, zona, espacio y placa quedan fijos.
 *
 * Editar (/infractions/:id/edit): tipo, gravedad, descripción, evidencia y observaciones,
 * solo si está pendiente y sin pagos. La multa se cambia desde el detalle.
 */
@Component({
  selector: 'app-infraction-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    CardComponent,
    CardBodyComponent,
    CardHeaderComponent,
    ButtonDirective,
    SpinnerComponent,
    AlertComponent
  ],
  templateUrl: './infraction-form.component.html',
  styleUrls: ['./infraction-form.component.css']
})
export class InfractionFormComponent implements OnInit {

  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly infractionService = inject(InfractionService);
  private readonly parkingService = inject(ParkingService);
  private readonly zoneService = inject(ZoneService);
  private readonly spaceService = inject(SpaceService);
  private readonly transactionService = inject(TransactionService);
  private readonly assignmentService = inject(OperatorAssignmentService);
  private readonly permissions = inject(PermissionService);
  private readonly destroyRef = inject(DestroyRef);

  readonly types = INFRACTION_TYPES;
  readonly severities = INFRACTION_SEVERITIES;
  readonly fineMax = INFRACTION_FINE_MAX;
  readonly evidenceMax = INFRACTION_EVIDENCE_MAX;
  readonly maxDetectedAt = localDateTimeInput();
  readonly minDueDate = localDate();

  editId: number | null = null;
  infraction: Infraction | null = null;
  stay: TransactionDetailResponse | null = null;

  parkings: Parking[] = [];
  zones: Zone[] = [];
  spaces: Space[] = [];
  /** Zonas permitidas por parqueo: null = todo el parqueo */
  private allowedZones = new Map<number, Set<number> | null>();
  private readonly anyParking = this.permissions.hasPermission(ANY_PARKING);

  loading = true;
  saving = false;
  errorMessage = '';

  readonly form = this.fb.group({
    parkingId: this.fb.control<number | null>(null, Validators.required),
    zoneId: this.fb.control<number | null>({ value: null, disabled: true }),
    spaceId: this.fb.control<number | null>({ value: null, disabled: true }),
    plateNumber: this.fb.control('', [Validators.required, plateValidator]),
    infractionType: this.fb.control<InfractionType | null>(null, Validators.required),
    severity: this.fb.control<InfractionSeverity | null>('MINOR', Validators.required),
    description: this.fb.control('', [Validators.required, Validators.minLength(5), Validators.maxLength(1000)]),
    evidence: this.fb.control('', evidenceValidator),
    hasFine: this.fb.control(false),
    fineAmount: this.fb.control<number | null>({ value: null, disabled: true }),
    fineDueDate: this.fb.control<string | null>({ value: null, disabled: true }),
    detectedAt: this.fb.control(localDateTimeInput(), Validators.required),
    notes: this.fb.control('', Validators.maxLength(500))
  });

  get isEdit(): boolean {
    return this.editId !== null;
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.editId = Number(id);
      this.loadForEdit(this.editId);
      return;
    }

    this.form.controls.parkingId.valueChanges.pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(pid => this.onParkingChange(pid));
    this.form.controls.zoneId.valueChanges.pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(zid => this.onZoneChange(zid));
    this.form.controls.hasFine.valueChanges.pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(on => this.toggleFine(!!on));

    const transactionId = Number(this.route.snapshot.queryParamMap.get('transactionId')) || null;
    this.loadForCreate(transactionId);
  }

  // ========================= CARGA =========================

  private loadForCreate(transactionId: number | null): void {
    forkJoin({
      parkings: this.parkingService.getActive(),
      mine: this.anyParking
        ? of([] as OperatorAssignment[])
        : this.assignmentService.listMine('ACTIVE').pipe(catchError(() => of([] as OperatorAssignment[]))),
      stay: transactionId
        ? this.transactionService.getById(transactionId).pipe(catchError(() => of(null)))
        : of(null)
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: ({ parkings, mine, stay }) => {
        this.parkings = this.anyParking ? parkings : this.onDutyParkings(parkings, mine);
        this.loading = false;
        if (stay) {
          this.applyStay(stay);
        } else if (transactionId) {
          this.errorMessage = 'No se encontró la estadía indicada; registra la infracción sin ella.';
        } else if (this.parkings.length === 1) {
          this.form.patchValue({ parkingId: this.parkings[0].id ?? null });
        }
      },
      error: err => {
        this.errorMessage = apiError(err, 'No se pudieron cargar los parqueos');
        this.loading = false;
      }
    });
  }

  private loadForEdit(id: number): void {
    this.infractionService.getById(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: i => {
        this.infraction = i;
        this.loading = false;
        this.form.patchValue({
          parkingId: i.parkingId,
          plateNumber: i.plateNumber,
          infractionType: i.infractionType,
          severity: i.severity,
          description: i.description,
          evidence: i.evidence.join('\n'),
          notes: i.notes ?? ''
        });
        // Al editar solo cambian los datos descriptivos
        for (const c of ['parkingId', 'zoneId', 'spaceId', 'plateNumber', 'hasFine', 'fineAmount', 'fineDueDate', 'detectedAt'] as const) {
          this.form.controls[c].disable();
        }
        if (!i.editable) {
          this.form.disable();
        }
      },
      error: err => {
        this.errorMessage = apiError(err, 'No se pudo cargar la infracción');
        this.loading = false;
      }
    });
  }

  /** Parqueos donde el usuario está de turno ahora, y qué zonas atiende en cada uno */
  private onDutyParkings(parkings: Parking[], mine: OperatorAssignment[]): Parking[] {
    this.allowedZones.clear();
    for (const a of mine.filter(x => x.onDutyNow)) {
      const current = this.allowedZones.get(a.parkingId);
      if (a.scope === 'PARKING' || a.zoneId == null) {
        this.allowedZones.set(a.parkingId, null);
      } else if (current !== null) {
        const zones = current ?? new Set<number>();
        zones.add(a.zoneId);
        this.allowedZones.set(a.parkingId, zones);
      }
    }
    return parkings.filter(p => this.allowedZones.has(p.id!));
  }

  /** La estadía fija parqueo, zona, espacio y placa */
  private applyStay(stay: TransactionDetailResponse): void {
    this.stay = stay;
    if (!this.parkings.some(p => p.id === stay.parking.id)) {
      this.errorMessage = `No estás de turno en ${stay.parking.name}: no puedes registrar infracciones allí.`;
      return;
    }
    this.form.patchValue({ parkingId: stay.parking.id, plateNumber: stay.vehicle.plateNumber });
    this.form.controls.parkingId.disable({ emitEvent: false });
    this.form.controls.plateNumber.disable();
    this.zones = [{ id: stay.zone.id, name: stay.zone.name, code: stay.zone.code } as Zone];
    this.spaces = [{ id: stay.space.id, code: stay.space.code } as Space];
    this.form.controls.zoneId.setValue(stay.zone.id, { emitEvent: false });
    this.form.controls.spaceId.setValue(stay.space.id, { emitEvent: false });
  }

  // ========================= PARQUEO, ZONA Y ESPACIO =========================

  /** Con una asignación solo de zona, la zona es obligatoria (la regla 8 la exige) */
  get zoneRequired(): boolean {
    const pid = this.form.controls.parkingId.value;
    return !!pid && !this.anyParking && this.allowedZones.get(pid) instanceof Set;
  }

  private onParkingChange(parkingId: number | null): void {
    if (this.stay) return;
    const zone = this.form.controls.zoneId;
    zone.reset(null, { emitEvent: false });
    zone.disable({ emitEvent: false });
    this.resetSpaces();
    this.zones = [];
    if (!parkingId) return;

    this.zoneService.getActive(parkingId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: zones => {
        const allowed = this.allowedZones.get(parkingId);
        this.zones = allowed ? zones.filter(z => allowed.has(z.id!)) : zones;
        zone.setValidators(this.zoneRequired ? Validators.required : null);
        zone.enable({ emitEvent: false });
        if (this.zones.length === 1 && this.zoneRequired) {
          zone.setValue(this.zones[0].id ?? null);
        }
        zone.updateValueAndValidity({ emitEvent: false });
      },
      error: err => (this.errorMessage = apiError(err, 'No se pudieron cargar las zonas'))
    });
  }

  private onZoneChange(zoneId: number | null): void {
    if (this.stay) return;
    this.resetSpaces();
    if (!zoneId) return;
    this.spaceService.getByZoneId(zoneId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: spaces => {
        this.spaces = [...spaces].sort((a, b) => a.code.localeCompare(b.code, 'es', { numeric: true }));
        this.form.controls.spaceId.enable({ emitEvent: false });
      },
      error: () => (this.spaces = [])
    });
  }

  private resetSpaces(): void {
    this.spaces = [];
    this.form.controls.spaceId.reset(null, { emitEvent: false });
    this.form.controls.spaceId.disable({ emitEvent: false });
  }

  // ========================= MULTA =========================

  private toggleFine(on: boolean): void {
    const amount = this.form.controls.fineAmount;
    const due = this.form.controls.fineDueDate;
    if (on) {
      amount.setValidators([Validators.required, Validators.min(0.01), Validators.max(INFRACTION_FINE_MAX),
        Validators.pattern(/^\d+(\.\d{1,2})?$/)]);
      amount.enable();
      due.enable();
      if (!due.value) {
        const d = new Date();
        d.setDate(d.getDate() + DEFAULT_DUE_DAYS);
        due.setValue(localDate(d));
      }
    } else {
      amount.clearValidators();
      amount.reset(null);
      amount.disable();
      due.reset(null);
      due.disable();
    }
  }

  // ========================= ENVIAR =========================

  invalid(name: keyof typeof this.form.controls): boolean {
    const c = this.form.controls[name];
    return c.invalid && (c.touched || c.dirty);
  }

  get platePreview(): string {
    return normalizePlate(this.form.controls.plateNumber.value);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (!this.isEdit && (this.form.controls.detectedAt.value ?? '') > localDateTimeInput()) {
      this.errorMessage = 'La fecha de la infracción no puede estar en el futuro';
      return;
    }
    const v = this.form.getRawValue();
    this.saving = true;
    this.errorMessage = '';

    const request$ = this.isEdit
      ? this.infractionService.edit(this.editId!, {
          infractionType: v.infractionType!, severity: v.severity!, description: v.description!.trim(),
          evidence: splitLines(v.evidence), notes: v.notes?.trim() || null
        })
      : this.infractionService.register({
          parkingId: v.parkingId!, zoneId: v.zoneId, spaceId: v.spaceId,
          transactionId: this.stay?.id ?? null,
          plateNumber: v.plateNumber!, infractionType: v.infractionType!, severity: v.severity!,
          description: v.description!.trim(), evidence: splitLines(v.evidence),
          fineAmount: v.hasFine ? Number(v.fineAmount) : null,
          fineDueDate: v.hasFine ? v.fineDueDate : null,
          detectedAt: v.detectedAt ? `${v.detectedAt}:00` : null,
          notes: v.notes?.trim() || null
        });

    request$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: saved => {
        const message = this.isEdit
          ? `Infracción ${saved.infractionCode} actualizada`
          : `Infracción ${saved.infractionCode} registrada para ${saved.plateNumber}`;
        this.router.navigate(['/infractions', saved.id], { state: { successMessage: message } });
      },
      error: err => {
        this.saving = false;
        this.errorMessage = apiError(err, 'No se pudo guardar la infracción');
      }
    });
  }

  cancelLink(): (string | number)[] {
    if (this.isEdit) return ['/infractions', this.editId!];
    return this.stay ? ['/transactions', this.stay.id] : ['/infractions'];
  }
}
