import { Component, DestroyRef, Input, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { EMPTY, catchError, exhaustMap } from 'rxjs';
import { OperatorAssignmentService } from '../../../../../core/services/parking-service/operator-assignment.service';
import { UserService } from '../../../../../core/services/auth-service/user.service';
import { AuthContextService } from '../../../../../core/services/auth-service/auth-context.service';
import { OperatorAssignment } from '../../../../../core/models/parking-service/operator-assignment.model';
import { Operator } from '../../../../../core/models/parking-service/operator.model';
import { ParkingShiftRate } from '../../../../../core/models/parking-service/parking-shift-rate.model';
import { Zone } from '../../../../../core/models/parking-service/zone.model';
import { liveRefresh } from '../../../../../core/utils/live-refresh.util';
import { apiError, assignmentStatus, formatLocalDate, localDate } from '../../../../../core/utils/parking.util';
import { hhmm } from '../../../../../core/utils/shift.util';

type View = 'current' | 'ended' | 'all';

/**
 * Operadores del parqueo por turno (en su detalle). Lista en vivo con quién está de
 * turno ahora; asignar, cambiar fechas y finalizar según los permisos operator-assignments.*.
 * Las reglas (un operador de parqueo o uno por zona, sin cruces del mismo operador) las
 * valida el backend y su mensaje se muestra tal cual.
 */
@Component({
  selector: 'app-parking-operators',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './parking-operators.component.html',
})
export class ParkingOperatorsComponent implements OnInit {

  @Input() parkingId!: number;
  @Input() zones: Zone[] = [];
  @Input() schedule: ParkingShiftRate[] = [];

  assignments: OperatorAssignment[] = [];
  view: View = 'current';
  loading = true;
  errorMessage = '';
  successMessage = '';

  // Asignar
  showAssign = false;
  operators: Operator[] | null = [];
  assignForm!: FormGroup;
  saving = false;
  formError = '';

  // Cambiar fechas
  editing: OperatorAssignment | null = null;
  datesForm!: FormGroup;

  // Finalizar
  toFinish: OperatorAssignment | null = null;

  readonly today = localDate();
  readonly status = assignmentStatus;
  readonly formatDate = formatLocalDate;
  readonly hhmm = hhmm;

  private destroyRef = inject(DestroyRef);

  constructor(
    private fb: FormBuilder,
    private service: OperatorAssignmentService,
    private userService: UserService,
    private authContext: AuthContextService,
  ) {}

  ngOnInit(): void {
    this.assignForm = this.fb.group({
      operatorId: [null, Validators.required],
      shiftId:    [null, Validators.required],
      zoneId:     [null],
      startDate:  [this.today, Validators.required],
      endDate:    [null],
    });
    this.datesForm = this.fb.group({ startDate: [null, Validators.required], endDate: [null] });
    this.load(false);
    liveRefresh(5000).pipe(
      exhaustMap(() => this.service.list({ parkingId: this.parkingId }, true).pipe(catchError(() => EMPTY))),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(list => (this.assignments = list));
  }

  load(silent: boolean): void {
    this.service.list({ parkingId: this.parkingId }, silent).subscribe({
      next: list => { this.loading = false; this.assignments = list; },
      error: err => { this.loading = false; this.errorMessage = apiError(err, 'No se pudieron cargar los operadores'); },
    });
  }

  can(permission: string): boolean {
    return this.authContext.hasPermission(permission);
  }

  /** Turnos en que atiende el parqueo (solo a esos se asigna personal) */
  get shifts(): ParkingShiftRate[] {
    return this.schedule.filter(s => s.usable);
  }

  get visible(): OperatorAssignment[] {
    const list = this.view === 'all' ? this.assignments
      : this.view === 'ended' ? this.assignments.filter(a => a.status === 'ENDED')
      : this.assignments.filter(a => a.status !== 'ENDED');
    // De turno primero, luego por hora de inicio del turno
    return [...list].sort((a, b) => Number(b.onDutyNow) - Number(a.onDutyNow)
      || (a.shiftStartTime ?? '').localeCompare(b.shiftStartTime ?? ''));
  }

  get onDuty(): OperatorAssignment[] {
    return this.assignments.filter(a => a.onDutyNow);
  }

  // ========================= ASIGNAR =========================

  openAssign(): void {
    this.formError = '';
    this.assignForm.reset({ operatorId: null, shiftId: this.shifts[0]?.shiftId ?? null, zoneId: null,
      startDate: this.today, endDate: null });
    this.showAssign = true;
    if (this.operators && this.operators.length === 0) {
      this.userService.getOperators().subscribe({
        next: ops => (this.operators = [...ops].sort((a, b) =>
          `${a.firstName} ${a.lastName}`.localeCompare(`${b.firstName} ${b.lastName}`))),
        error: () => (this.operators = null),
      });
    }
  }

  assign(): void {
    if (this.assignForm.invalid) {
      this.assignForm.markAllAsTouched();
      return;
    }
    const v = this.assignForm.getRawValue();
    if (v.endDate && v.endDate < v.startDate) {
      this.formError = 'La fecha de fin no puede ser anterior a la de inicio.';
      return;
    }
    this.saving = true;
    this.formError = '';
    this.service.assign({
      operatorId: Number(v.operatorId), parkingId: this.parkingId, zoneId: v.zoneId ? Number(v.zoneId) : null,
      shiftId: Number(v.shiftId), startDate: v.startDate, endDate: v.endDate || null,
    }).subscribe({
      next: a => {
        this.saving = false;
        this.showAssign = false;
        this.flash(`${a.operatorName} asignado al turno ${a.shiftName}`);
        this.load(true);
      },
      error: err => { this.saving = false; this.formError = apiError(err, 'No se pudo asignar'); },
    });
  }

  // ========================= CAMBIAR FECHAS =========================

  openDates(a: OperatorAssignment): void {
    this.formError = '';
    this.editing = a;
    this.datesForm.reset({ startDate: a.startDate, endDate: a.endDate ?? null });
    a.status === 'PENDING' ? this.datesForm.get('startDate')?.enable() : this.datesForm.get('startDate')?.disable();
  }

  saveDates(): void {
    const a = this.editing;
    if (!a) return;
    const v = this.datesForm.getRawValue();
    this.saving = true;
    this.formError = '';
    this.service.update(a.id, {
      startDate: a.status === 'PENDING' ? v.startDate : undefined,
      endDate: v.endDate || null,
    }).subscribe({
      next: () => { this.saving = false; this.editing = null; this.flash('Fechas actualizadas'); this.load(true); },
      error: err => { this.saving = false; this.formError = apiError(err, 'No se pudieron cambiar las fechas'); },
    });
  }

  // ========================= FINALIZAR =========================

  /** Si no cumplió un día (empieza hoy o después) se cancela en lugar de finalizar */
  willCancel(a: OperatorAssignment): boolean {
    return a.startDate >= this.today;
  }

  finish(): void {
    const a = this.toFinish;
    if (!a) return;
    this.toFinish = null;
    this.service.finish(a.id).subscribe({
      next: result => {
        this.flash(result ? `Asignación de ${a.operatorName} finalizada` : `Asignación de ${a.operatorName} cancelada`);
        this.load(true);
      },
      error: err => (this.errorMessage = apiError(err, 'No se pudo finalizar la asignación')),
    });
  }

  operatorName(o: Operator): string {
    return `${o.firstName} ${o.lastName}`.trim();
  }

  private flash(message: string): void {
    this.successMessage = message;
    setTimeout(() => (this.successMessage = ''), 4000);
  }
}
