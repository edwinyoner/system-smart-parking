// steps/step5-operators/step5-operators.component.ts
import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { concatMap, finalize, from, tap } from 'rxjs';
import { UserService } from '../../../../../core/services/auth-service/user.service';
import { OperatorAssignmentService } from '../../../../../core/services/parking-service/operator-assignment.service';
import { Operator } from '../../../../../core/models/parking-service/operator.model';
import { Parking } from '../../../../../core/models/parking-service/parking.model';
import { ParkingShiftRate } from '../../../../../core/models/parking-service/parking-shift-rate.model';
import { apiError, formatLocalDate, localDate } from '../../../../../core/utils/parking.util';
import { hhmm, shiftsOverlap } from '../../../../../core/utils/shift.util';
import { PlannedZone } from '../step2-zones/step2-zones.component';

interface AssignmentRow {
  operatorId: number;
  operatorName: string;
  zoneId: number | null;       // null = todo el parqueo
  zoneName: string;
  shiftId: number;
  shiftName: string;
  startDate: string;
  endDate: string | null;
  saved: boolean;
}

/**
 * Paso 5: operadores del parqueo por turno. En un turno, el parqueo tiene un operador
 * para todo el parqueo o uno por zona, y un operador no puede estar en dos turnos que
 * se crucen. El backend lo valida (409); aquí se avisa antes para no enviar en vano.
 */
@Component({
  selector: 'app-step5-operators',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './step5-operators.component.html',
  styleUrl: './step5-operators.component.css',
})
export class Step5OperatorsComponent implements OnInit {

  @Input() parking!: Parking;
  @Input() zones: PlannedZone[] = [];
  @Input() schedule: ParkingShiftRate[] = [];
  /** Emite cuántas asignaciones se guardaron (0 = se dejó para después) */
  @Output() completed = new EventEmitter<number>();

  /** null = sin permiso para ver usuarios */
  operators: Operator[] | null = [];
  rows: AssignmentRow[] = [];
  form!: FormGroup;

  loadingCatalogs = true;
  loadingSave = false;
  errorMessage = '';
  formError = '';
  readonly today = localDate();
  readonly hhmm = hhmm;
  readonly formatDate = formatLocalDate;

  constructor(
    private fb: FormBuilder,
    private userService: UserService,
    private assignmentService: OperatorAssignmentService,
  ) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      operatorId: [null, Validators.required],
      zoneId:     [null],
      shiftId:    [this.shifts[0]?.shiftId ?? null, Validators.required],
      startDate:  [this.today, Validators.required],
      endDate:    [null],
    });
    this.userService.getOperators().subscribe({
      next: ops => {
        this.operators = [...ops].sort((a, b) => `${a.firstName} ${a.lastName}`.localeCompare(`${b.firstName} ${b.lastName}`));
        this.loadingCatalogs = false;
      },
      error: () => {
        this.operators = null;
        this.loadingCatalogs = false;
      },
    });
  }

  /** Turnos en que atiende el parqueo (solo a esos se asigna personal) */
  get shifts(): ParkingShiftRate[] {
    return this.schedule.filter(s => s.usable);
  }

  operatorName(o: Operator): string {
    return `${o.firstName} ${o.lastName}`.trim();
  }

  get pendingCount(): number {
    return this.rows.filter(r => !r.saved).length;
  }

  get savedCount(): number {
    return this.rows.filter(r => r.saved).length;
  }

  // ========================= AGREGAR A LA LISTA =========================

  addRow(): void {
    this.formError = '';
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const operatorId = Number(v.operatorId);
    const shiftId = Number(v.shiftId);
    const zoneId = v.zoneId ? Number(v.zoneId) : null;
    if (v.startDate < this.today) {
      this.formError = 'La asignación no puede empezar en una fecha pasada.';
      return;
    }
    if (v.endDate && v.endDate < v.startDate) {
      this.formError = 'La fecha de fin no puede ser anterior a la de inicio.';
      return;
    }
    const conflict = this.findConflict(operatorId, zoneId, shiftId, v.startDate, v.endDate || null);
    if (conflict) {
      this.formError = conflict;
      return;
    }
    const op = this.operators?.find(o => o.id === operatorId);
    const shift = this.shifts.find(s => s.shiftId === shiftId);
    const zone = this.zones.find(z => z.zone.id === zoneId)?.zone;
    this.rows.push({
      operatorId,
      operatorName: op ? this.operatorName(op) : `Operador ${operatorId}`,
      zoneId,
      zoneName: zone ? `${zone.name} (${zone.code})` : 'Todo el parqueo',
      shiftId,
      shiftName: shift ? `${shift.shiftName} (${hhmm(shift.startTime)} - ${hhmm(shift.endTime)})` : `Turno ${shiftId}`,
      startDate: v.startDate,
      endDate: v.endDate || null,
      saved: false,
    });
    this.form.patchValue({ operatorId: null, zoneId: null });
    this.form.markAsUntouched();
  }

  removeRow(i: number): void {
    if (!this.rows[i].saved) {
      this.rows.splice(i, 1);
    }
  }

  /** Mismas reglas que el backend, contra las filas ya agregadas */
  private findConflict(operatorId: number, zoneId: number | null, shiftId: number,
                       start: string, end: string | null): string | null {
    const datesCross = (r: AssignmentRow) =>
      r.startDate <= (end ?? '9999-12-31') && start <= (r.endDate ?? '9999-12-31');
    const shift = this.shifts.find(s => s.shiftId === shiftId);
    for (const r of this.rows.filter(datesCross)) {
      if (r.shiftId === shiftId) {
        if (r.zoneId === null || zoneId === null) {
          return r.zoneId === null
            ? `${r.operatorName} ya atiende todo el parqueo en ese turno: no se puede asignar también por zona.`
            : 'Ese turno ya tiene operadores por zona: asigna por zona o quítalos de la lista.';
        }
        if (r.zoneId === zoneId) return `${r.operatorName} ya atiende esa zona en ese turno.`;
      }
      if (r.operatorId === operatorId) {
        const other = this.shifts.find(s => s.shiftId === r.shiftId);
        if (r.shiftId === shiftId || shiftsOverlap(other?.startTime, other?.endTime, shift?.startTime, shift?.endTime)) {
          return `${r.operatorName} ya tiene un turno que se cruza con este en esas fechas.`;
        }
      }
    }
    return null;
  }

  // ========================= GUARDAR =========================

  onFinish(): void {
    const pending = this.rows.filter(r => !r.saved);
    if (pending.length === 0) {
      this.completed.emit(this.savedCount);
      return;
    }
    this.loadingSave = true;
    this.errorMessage = '';
    let current: AssignmentRow | null = null;

    from(pending).pipe(
      concatMap(row => {
        current = row;
        return this.assignmentService.assign({
          operatorId: row.operatorId,
          parkingId: this.parking.id!,
          zoneId: row.zoneId,
          shiftId: row.shiftId,
          startDate: row.startDate,
          endDate: row.endDate,
        }).pipe(tap(() => (row.saved = true)));
      }),
      finalize(() => (this.loadingSave = false)),
    ).subscribe({
      error: err => {
        this.errorMessage = apiError(err, `No se pudo asignar a ${current?.operatorName}`) +
          (this.savedCount ? `. Las ${this.savedCount} asignaciones marcadas ya quedaron guardadas.` : '') +
          ' Quita o corrige la que falló y vuelve a intentar.';
      },
      complete: () => this.completed.emit(this.savedCount),
    });
  }

  skip(): void {
    this.completed.emit(this.savedCount);
  }
}
