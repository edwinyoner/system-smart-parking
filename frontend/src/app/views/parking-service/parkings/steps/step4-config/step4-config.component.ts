// steps/step4-config/step4-config.component.ts
import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { ShiftService } from '../../../../../core/services/parking-service/shift.service';
import { RateService } from '../../../../../core/services/parking-service/rate.service';
import { ParkingShiftRateService } from '../../../../../core/services/parking-service/parking-shift-rate.service';
import { Shift } from '../../../../../core/models/parking-service/shift.model';
import { Rate } from '../../../../../core/models/parking-service/rate.model';
import { ParkingShiftRate } from '../../../../../core/models/parking-service/parking-shift-rate.model';
import { apiError } from '../../../../../core/utils/parking.util';
import { hhmm, shiftsOverlap } from '../../../../../core/utils/shift.util';

/** Máximo de turnos por parqueo (igual que el backend) */
export const MAX_SCHEDULE_ROWS = 24;

/**
 * Paso 4: horario del parqueo (qué turnos atiende y con qué tarifa). Vale para todas
 * sus zonas. Sin horario el parqueo no recibe vehículos; se puede configurar después.
 */
@Component({
  selector: 'app-step4-config',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './step4-config.component.html',
  styleUrl: './step4-config.component.css',
})
export class Step4ConfigComponent implements OnInit {

  @Input() parkingId!: number;
  /** Horario actual para editarlo (desde el detalle del parqueo) */
  @Input() initial: ParkingShiftRate[] = [];
  /** true = dentro del detalle: "Cancelar" en lugar de "Configurar después" */
  @Input() embedded = false;
  @Output() configSaved = new EventEmitter<ParkingShiftRate[]>();
  @Output() cancelled = new EventEmitter<void>();

  shifts: Shift[] = [];
  rates: Rate[] = [];
  form: FormGroup;

  loadingCatalogs = true;
  loadingSave = false;
  errorMessage = '';
  catalogError = '';
  readonly maxRows = MAX_SCHEDULE_ROWS;
  readonly hhmm = hhmm;

  constructor(
    private fb: FormBuilder,
    private shiftService: ShiftService,
    private rateService: RateService,
    private parkingShiftRateService: ParkingShiftRateService,
  ) {
    this.form = this.fb.group({ configurations: this.fb.array([]) });
  }

  ngOnInit(): void {
    this.loadCatalogs();
  }

  get configurations(): FormArray {
    return this.form.get('configurations') as FormArray;
  }

  loadCatalogs(): void {
    this.loadingCatalogs = true;
    this.catalogError = '';
    forkJoin({ shifts: this.shiftService.getActive(), rates: this.rateService.getActive() }).subscribe({
      next: ({ shifts, rates }) => {
        this.shifts = [...shifts].sort((a, b) => a.startTime.localeCompare(b.startTime));
        this.rates = rates;
        this.loadingCatalogs = false;
        if (this.configurations.length > 0) return;
        if (this.initial.length) {
          this.loadInitial();
        } else if (!this.embedded && this.shifts.length && this.rates.length) {
          this.addConfiguration();
        }
      },
      error: () => {
        this.loadingCatalogs = false;
        this.catalogError = 'No se pudieron cargar los turnos y tarifas. Intenta recargar.';
      },
    });
  }

  /**
   * Carga el horario actual. Un turno o tarifa que ya no está activo se agrega igual al
   * catálogo de la fila, para que se vea y se pueda cambiar o desactivar.
   */
  private loadInitial(): void {
    for (const c of this.initial) {
      if (!this.shifts.some(s => s.id === c.shiftId)) {
        this.shifts.push({ id: c.shiftId, name: `${c.shiftName} (inactivo)`, code: c.shiftCode ?? '',
          startTime: c.startTime ?? '00:00', endTime: c.endTime ?? '00:00', status: false,
          crossesMidnight: c.crossesMidnight });
      }
      if (!this.rates.some(r => r.id === c.rateId)) {
        this.rates.push({ id: c.rateId, name: `${c.rateName} (inactiva)`, amount: c.rateAmount ?? 0,
          currency: c.rateCurrency ?? 'PEN', billingUnitMinutes: 60, minimumMinutes: 0, graceMinutes: 0,
          toleranceMinutes: 0, status: false });
      }
      this.configurations.push(this.fb.group({
        shiftId: [c.shiftId, Validators.required],
        rateId:  [c.rateId, Validators.required],
        status:  [c.status],
      }));
    }
  }

  addConfiguration(): void {
    if (this.configurations.length >= MAX_SCHEDULE_ROWS) return;
    // Propone el primer turno libre y la tarifa de la fila anterior
    const used = new Set(this.configurations.controls.map(c => c.get('shiftId')?.value));
    const nextShift = this.shifts.find(s => !used.has(s.id));
    const lastRate = this.configurations.length
      ? this.configurations.at(this.configurations.length - 1).get('rateId')?.value
      : this.rates[0]?.id ?? null;
    this.configurations.push(this.fb.group({
      shiftId: [nextShift?.id ?? null, Validators.required],
      rateId:  [lastRate, Validators.required],
      status:  [true],
    }));
  }

  removeConfiguration(i: number): void {
    this.configurations.removeAt(i);
  }

  get canAddMore(): boolean {
    return this.configurations.length < MAX_SCHEDULE_ROWS && this.configurations.length < this.shifts.length;
  }

  /** Turnos que se pueden elegir en la fila (excluye los elegidos en otras filas) */
  availableShifts(i: number): Shift[] {
    const current = this.configurations.at(i).get('shiftId')?.value;
    const usedElsewhere = new Set(this.configurations.controls
      .filter((_, k) => k !== i).map(c => c.get('shiftId')?.value));
    return this.shifts.filter(s => s.id === current || !usedElsewhere.has(s.id));
  }

  shift(id: number | null): Shift | undefined {
    return this.shifts.find(s => s.id === Number(id));
  }

  rateLabel(rate: Rate): string {
    return `${rate.name} - S/ ${Number(rate.amount).toFixed(2)} por hora`;
  }

  /**
   * Pares de filas activas cuyos turnos se solapan: el backend responde 409 porque
   * a cada minuto del parqueo le corresponde una sola tarifa.
   */
  get overlaps(): string[] {
    const active = this.configurations.controls
      .filter(c => c.get('status')?.value)
      .map(c => this.shift(c.get('shiftId')?.value))
      .filter((s): s is Shift => !!s);
    const result: string[] = [];
    for (let a = 0; a < active.length; a++) {
      for (let b = a + 1; b < active.length; b++) {
        if (shiftsOverlap(active[a].startTime, active[a].endTime, active[b].startTime, active[b].endTime)) {
          result.push(`${active[a].name} (${hhmm(active[a].startTime)}-${hhmm(active[a].endTime)}) y ` +
            `${active[b].name} (${hhmm(active[b].startTime)}-${hhmm(active[b].endTime)})`);
        }
      }
    }
    return result;
  }

  /** En el detalle se puede guardar un horario vacío (el parqueo deja de atender) */
  get canSubmit(): boolean {
    return this.form.valid && (this.embedded || this.configurations.length > 0) && this.overlaps.length === 0;
  }

  onSubmit(): void {
    if (!this.canSubmit) {
      this.form.markAllAsTouched();
      return;
    }
    this.loadingSave = true;
    this.errorMessage = '';
    const configurations = this.configurations.getRawValue().map((c: any) => ({
      shiftId: Number(c.shiftId), rateId: Number(c.rateId), status: !!c.status,
    }));
    this.parkingShiftRateService.configure(this.parkingId, { configurations }).subscribe({
      next: saved => {
        this.loadingSave = false;
        this.configSaved.emit(saved);
      },
      error: err => {
        this.loadingSave = false;
        this.errorMessage = apiError(err, 'No se pudo guardar el horario. Intenta nuevamente.');
      },
    });
  }

  /** Continúa sin horario: el parqueo no recibirá vehículos hasta configurarlo */
  skip(): void {
    this.configSaved.emit([]);
  }
}
