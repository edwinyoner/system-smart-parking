// src/app/views/parking-service/transactions/transaction-entry/transaction-entry.component.ts
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import {
  CardComponent,
  CardBodyComponent,
  CardHeaderComponent,
  ColComponent,
  RowComponent,
  FormDirective,
  FormControlDirective,
  FormLabelDirective,
  FormSelectDirective,
  FormCheckComponent,
  FormCheckInputDirective,
  FormCheckLabelDirective,
  ButtonDirective,
  SpinnerComponent,
  AlertComponent
} from '@coreui/angular';

import { ParkingService } from '../../../../core/services/parking-service/parking.service';
import { ZoneService } from '../../../../core/services/parking-service/zone.service';
import { SpaceService } from '../../../../core/services/parking-service/space.service';
import { TransactionService } from '../../../../core/services/parking-service/transaction.service';
import { DocumentTypeService } from '../../../../core/services/parking-service/document-type.service';
import { ParkingShiftRateService } from '../../../../core/services/parking-service/parking-shift-rate.service';
import { OperatorAssignmentService } from '../../../../core/services/parking-service/operator-assignment.service';
import { PermissionService } from '../../../../core/services/auth-service/permission.service';

import { Parking } from '../../../../core/models/parking-service/parking.model';
import { Zone } from '../../../../core/models/parking-service/zone.model';
import { Space } from '../../../../core/models/parking-service/space.model';
import { DocumentType } from '../../../../core/models/parking-service/document-type.model';
import { ParkingScheduleStatus } from '../../../../core/models/parking-service/parking-shift-rate.model';
import { OperatorAssignment } from '../../../../core/models/parking-service/operator-assignment.model';
import { TransactionDetailResponse, TransactionEntryRequest } from '../../../../core/models/parking-service/transaction.model';

import { normalizePlate, plateValidator } from '../../../../core/utils/plate.util';
import { documentNumberError, describeLength, normalizeDocumentNumber } from '../../../../core/utils/document-type.util';
import { apiError } from '../../../../core/utils/parking.util';
import { PHONE_ERROR_MESSAGE, PHONE_PATTERN, keepPhoneDigits } from '../../../../core/utils/phone.util';

/** Permiso para registrar en cualquier parqueo sin estar de turno (lo tiene ADMIN) */
const ANY_PARKING = 'transactions.any-parking';

/**
 * Entrada manual de un vehículo: el respaldo del operador cuando la cámara falla.
 *
 * - Solo se ofrecen los parqueos (y zonas) donde el usuario está de turno ahora; quien tiene
 *   transactions.any-parking ve todos. El backend lo vuelve a comprobar (403).
 * - La placa se normaliza (abc123 -> ABC-123) con la misma regla que el backend.
 * - El espacio es opcional: si no se elige, el sistema descuenta uno libre al azar de la zona
 *   (es solo para la disponibilidad; el conductor estaciona donde quiera).
 * - El documento del conductor es opcional, salvo que el parqueo lo exija.
 * - Al registrar se queda en la pantalla con el parqueo y la zona elegidos, para el siguiente.
 */
@Component({
  selector: 'app-transaction-entry',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    CardComponent,
    CardBodyComponent,
    CardHeaderComponent,
    ColComponent,
    RowComponent,
    FormDirective,
    FormControlDirective,
    FormLabelDirective,
    FormSelectDirective,
    FormCheckComponent,
    FormCheckInputDirective,
    FormCheckLabelDirective,
    ButtonDirective,
    SpinnerComponent,
    AlertComponent
  ],
  templateUrl: './transaction-entry.component.html',
  styleUrls: ['./transaction-entry.component.css']
})
export class TransactionEntryComponent implements OnInit {

  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);
  private readonly parkingService = inject(ParkingService);
  private readonly zoneService = inject(ZoneService);
  private readonly spaceService = inject(SpaceService);
  private readonly transactionService = inject(TransactionService);
  private readonly documentTypeService = inject(DocumentTypeService);
  private readonly scheduleService = inject(ParkingShiftRateService);
  private readonly assignmentService = inject(OperatorAssignmentService);
  private readonly permissions = inject(PermissionService);

  entryForm!: FormGroup;

  // ========================= DATOS =========================
  parkings: Parking[] = [];
  zones: Zone[] = [];
  spaces: Space[] = [];
  documentTypes: DocumentType[] = [];
  schedule: ParkingScheduleStatus | null = null;

  /** Zonas permitidas por parqueo: null = todo el parqueo (asignación de parqueo o any-parking) */
  private allowedZones = new Map<number, Set<number> | null>();
  readonly anyParking = this.permissions.hasPermission(ANY_PARKING);

  // ========================= ESTADO =========================
  loading = true;
  loadingParking = false;
  loadingSpaces = false;
  submitting = false;
  errorMessage = '';
  lastEntry: TransactionDetailResponse | null = null;

  ngOnInit(): void {
    this.buildForm();
    this.loadInitialData();
  }

  // ========================= FORMULARIO =========================

  private buildForm(): void {
    this.entryForm = this.fb.group({
      parkingId: [null, Validators.required],
      zoneId: [{ value: null, disabled: true }, Validators.required],
      spaceId: [{ value: null, disabled: true }],          // null = al azar
      plateNumber: ['', [Validators.required, plateValidator]],
      withDriver: [false],
      documentTypeId: [null],
      documentNumber: [''],
      customerFirstName: ['', Validators.maxLength(100)],
      customerLastName: ['', Validators.maxLength(100)],
      customerPhone: ['', Validators.pattern(PHONE_PATTERN)],
      customerEmail: ['', [Validators.email, Validators.maxLength(100)]],
      notes: ['', Validators.maxLength(500)]
    });

    this.entryForm.get('parkingId')!.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(id => this.onParkingChange(id));
    this.entryForm.get('zoneId')!.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(id => this.onZoneChange(id));
    this.entryForm.get('withDriver')!.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.applyDocumentValidators());
  }

  // ========================= CARGA INICIAL =========================

  private loadInitialData(): void {
    forkJoin({
      parkings: this.parkingService.getActive(),
      documentTypes: this.documentTypeService.getActive().pipe(catchError(() => of([] as DocumentType[]))),
      mine: this.anyParking
        ? of([] as OperatorAssignment[])
        : this.assignmentService.listMine('ACTIVE').pipe(catchError(() => of([] as OperatorAssignment[])))
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: ({ parkings, documentTypes, mine }) => {
        this.documentTypes = documentTypes;
        this.parkings = this.anyParking ? parkings : this.onDutyParkings(parkings, mine);
        this.loading = false;
        // Con un solo parqueo posible, se elige solo
        if (this.parkings.length === 1) {
          this.entryForm.patchValue({ parkingId: this.parkings[0].id });
        }
      },
      error: err => {
        this.errorMessage = apiError(err, 'No se pudieron cargar los parqueos');
        this.loading = false;
      }
    });
  }

  /** Parqueos donde el usuario está de turno ahora, y qué zonas puede atender en cada uno */
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

  // ========================= PARQUEO, ZONA Y ESPACIO =========================

  get selectedParking(): Parking | undefined {
    return this.parkings.find(p => p.id === this.entryForm.get('parkingId')?.value);
  }

  /** El parqueo exige el documento del conductor (anti-robo) */
  get documentRequired(): boolean {
    return !!this.selectedParking?.requireDriverDocument;
  }

  get isOpen(): boolean {
    return !!this.schedule?.open;
  }

  private onParkingChange(parkingId: number | null): void {
    const zone = this.entryForm.get('zoneId')!;
    zone.reset(null, { emitEvent: false });
    zone.disable({ emitEvent: false });
    this.resetSpaces();
    this.zones = [];
    this.schedule = null;
    this.applyDocumentValidators();
    if (!parkingId) {
      return;
    }

    this.loadingParking = true;
    forkJoin({
      zones: this.zoneService.getActive(parkingId),
      schedule: this.scheduleService.getCurrent(parkingId, true).pipe(catchError(() => of(null)))
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: ({ zones, schedule }) => {
        const allowed = this.allowedZones.get(parkingId);
        this.zones = allowed ? zones.filter(z => allowed.has(z.id!)) : zones;
        this.schedule = schedule;
        this.loadingParking = false;
        zone.enable({ emitEvent: false });
        if (this.zones.length === 1) {
          zone.setValue(this.zones[0].id);
        }
      },
      error: err => {
        this.errorMessage = apiError(err, 'No se pudieron cargar las zonas');
        this.loadingParking = false;
      }
    });
  }

  private onZoneChange(zoneId: number | null): void {
    this.resetSpaces();
    if (zoneId) {
      this.loadSpaces(zoneId);
    }
  }

  private loadSpaces(zoneId: number): void {
    this.loadingSpaces = true;
    this.spaceService.getAvailableByZoneId(zoneId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: spaces => {
          this.spaces = spaces;
          this.loadingSpaces = false;
          if (spaces.length > 0) {
            this.entryForm.get('spaceId')!.enable({ emitEvent: false });
          }
        },
        error: () => {
          this.spaces = [];
          this.loadingSpaces = false;
        }
      });
  }

  private resetSpaces(): void {
    const space = this.entryForm.get('spaceId')!;
    space.reset(null, { emitEvent: false });
    space.disable({ emitEvent: false });
    this.spaces = [];
  }

  get zoneFull(): boolean {
    return !!this.entryForm.get('zoneId')?.value && !this.loadingSpaces && this.spaces.length === 0;
  }

  // ========================= PLACA =========================

  /** Vista previa de cómo se guardará la placa */
  get platePreview(): string {
    return normalizePlate(this.entryForm.get('plateNumber')?.value);
  }

  onPlateInput(): void {
    const ctrl = this.entryForm.get('plateNumber')!;
    const upper = (ctrl.value ?? '').toUpperCase();
    if (upper !== ctrl.value) {
      ctrl.setValue(upper, { emitEvent: false });
    }
  }

  // ========================= DOCUMENTO =========================

  /** Se piden los datos del conductor si el operador lo marca o si el parqueo lo exige */
  get showDriver(): boolean {
    return this.documentRequired || !!this.entryForm.get('withDriver')?.value;
  }

  get selectedDocumentType(): DocumentType | undefined {
    return this.documentTypes.find(d => d.id === this.entryForm.get('documentTypeId')?.value);
  }

  get documentHint(): string {
    const type = this.selectedDocumentType;
    return type ? `${type.code}: ${describeLength(type)}` : 'Elige el tipo de documento';
  }

  /** Error del número según las reglas del tipo (longitud, solo dígitos) */
  get documentError(): string | null {
    const type = this.selectedDocumentType;
    const number = this.entryForm.get('documentNumber')?.value ?? '';
    if (!this.showDriver || !type) {
      return null;
    }
    return documentNumberError(type, number);
  }

  private applyDocumentValidators(): void {
    const type = this.entryForm.get('documentTypeId')!;
    const number = this.entryForm.get('documentNumber')!;
    if (this.showDriver) {
      type.setValidators(Validators.required);
      number.setValidators(Validators.required);
    } else {
      type.clearValidators();
      number.clearValidators();
    }
    type.updateValueAndValidity({ emitEvent: false });
    number.updateValueAndValidity({ emitEvent: false });
  }

  readonly phoneError = PHONE_ERROR_MESSAGE;

  /** Solo dígitos y máximo 9, también al pegar */
  onPhoneInput(event: Event): void {
    keepPhoneDigits(event, this.entryForm.get('customerPhone'));
  }

  // ========================= REGISTRAR =========================

  get canSubmit(): boolean {
    return this.entryForm.valid && !this.documentError && this.isOpen && !this.zoneFull && !this.submitting;
  }

  onSubmit(): void {
    this.entryForm.markAllAsTouched();
    if (!this.canSubmit) {
      return;
    }
    const v = this.entryForm.getRawValue();
    const driver = this.showDriver;
    const request: TransactionEntryRequest = {
      plateNumber: normalizePlate(v.plateNumber),
      parkingId: v.parkingId,
      zoneId: v.zoneId,
      spaceId: v.spaceId ?? null,
      documentTypeId: driver ? v.documentTypeId : null,
      documentNumber: driver ? normalizeDocumentNumber(v.documentNumber) : null,
      customerFirstName: driver ? this.text(v.customerFirstName) : null,
      customerLastName: driver ? this.text(v.customerLastName) : null,
      customerPhone: driver ? this.text(v.customerPhone) : null,
      customerEmail: driver ? this.text(v.customerEmail) : null,
      notes: this.text(v.notes)
    };

    this.submitting = true;
    this.errorMessage = '';
    this.lastEntry = null;
    this.transactionService.recordEntry(request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: entry => {
          this.lastEntry = entry;
          this.submitting = false;
          this.prepareNext();
        },
        error: err => {
          this.errorMessage = apiError(err, 'No se pudo registrar la entrada');
          this.submitting = false;
          // El espacio elegido pudo ocuparse mientras tanto: se recarga la lista
          const zoneId = this.entryForm.get('zoneId')?.value;
          if (zoneId) {
            this.resetSpaces();
            this.loadSpaces(zoneId);
          }
        }
      });
  }

  /** Deja parqueo y zona para el siguiente vehículo; limpia el resto */
  private prepareNext(): void {
    this.entryForm.patchValue({
      plateNumber: '', withDriver: false, documentTypeId: null, documentNumber: '',
      customerFirstName: '', customerLastName: '', customerPhone: '', customerEmail: '', notes: ''
    }, { emitEvent: false });
    this.applyDocumentValidators();
    this.entryForm.markAsUntouched();
    const zoneId = this.entryForm.get('zoneId')?.value;
    this.resetSpaces();
    if (zoneId) {
      this.loadSpaces(zoneId);
    }
  }

  private text(value: string | null | undefined): string | null {
    const t = (value ?? '').trim();
    return t ? t : null;
  }

  isFieldInvalid(name: string): boolean {
    const field = this.entryForm.get(name);
    return !!(field && field.invalid && (field.dirty || field.touched));
  }

  cancel(): void {
    this.router.navigate(['/transactions/active']);
  }
}
