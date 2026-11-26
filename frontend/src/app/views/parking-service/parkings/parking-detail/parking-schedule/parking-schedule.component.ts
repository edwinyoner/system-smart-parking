import { Component, DestroyRef, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { EMPTY, catchError, exhaustMap, forkJoin } from 'rxjs';
import { ParkingShiftRateService } from '../../../../../core/services/parking-service/parking-shift-rate.service';
import { ParkingScheduleStatus, ParkingShiftRate } from '../../../../../core/models/parking-service/parking-shift-rate.model';
import { liveRefresh } from '../../../../../core/utils/live-refresh.util';
import { apiError } from '../../../../../core/utils/parking.util';
import { hhmm } from '../../../../../core/utils/shift.util';
import { Step4ConfigComponent } from '../../steps/step4-config/step4-config.component';

/**
 * Horario del parqueo en su detalle: si atiende ahora, sus turnos con tarifa y las
 * acciones (activar o desactivar un turno, quitarlo, editar todo el horario).
 * Se actualiza en vivo cada 5 s.
 */
@Component({
  selector: 'app-parking-schedule',
  standalone: true,
  imports: [CommonModule, Step4ConfigComponent],
  templateUrl: './parking-schedule.component.html',
})
export class ParkingScheduleComponent implements OnInit {

  @Input() parkingId!: number;
  @Input() canEdit = false;
  /** Avisa al detalle cuando cambia (los operadores dependen de los turnos) */
  @Output() changed = new EventEmitter<ParkingShiftRate[]>();

  schedule: ParkingShiftRate[] = [];
  current: ParkingScheduleStatus | null = null;
  loading = true;
  errorMessage = '';
  successMessage = '';
  busyId: number | null = null;

  editing = false;
  toRemove: ParkingShiftRate | null = null;
  readonly hhmm = hhmm;

  private destroyRef = inject(DestroyRef);

  constructor(private service: ParkingShiftRateService) {}

  ngOnInit(): void {
    this.load(false);
    liveRefresh(5000).pipe(
      exhaustMap(() => this.request(true).pipe(catchError(() => EMPTY))),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(r => this.apply(r));
  }

  private request(silent: boolean) {
    return forkJoin({
      schedule: this.service.getByParkingId(this.parkingId, silent),
      current: this.service.getCurrent(this.parkingId, silent),
    });
  }

  load(silent: boolean): void {
    this.request(silent).subscribe({
      next: r => { this.loading = false; this.apply(r); },
      error: err => { this.loading = false; this.errorMessage = apiError(err, 'No se pudo cargar el horario'); },
    });
  }

  private apply(r: { schedule: ParkingShiftRate[]; current: ParkingScheduleStatus }): void {
    const changed = JSON.stringify(r.schedule) !== JSON.stringify(this.schedule);
    this.schedule = r.schedule;
    this.current = r.current;
    if (changed) this.changed.emit(r.schedule);
  }

  isCurrent(c: ParkingShiftRate): boolean {
    return !!this.current?.open && this.current.current?.id === c.id;
  }

  /** Por qué un turno activo no se usa (su turno o su tarifa están inactivos) */
  unusableReason(c: ParkingShiftRate): string | null {
    if (!c.status) return null;
    if (!c.shiftUsable) return 'El turno está inactivo o eliminado';
    if (!c.rateUsable) return 'La tarifa está inactiva o eliminada';
    return null;
  }

  toggle(c: ParkingShiftRate): void {
    if (!c.id) return;
    this.busyId = c.id;
    this.errorMessage = '';
    this.service.toggleStatus(c.id).subscribe({
      next: updated => {
        this.busyId = null;
        this.flash(`Turno ${updated.shiftName} ${updated.status ? 'activado' : 'desactivado'}`);
        this.load(true);
      },
      error: err => { this.busyId = null; this.errorMessage = apiError(err, 'No se pudo cambiar el turno'); },
    });
  }

  confirmRemove(c: ParkingShiftRate): void {
    this.toRemove = c;
  }

  remove(): void {
    const c = this.toRemove;
    if (!c?.id) return;
    this.toRemove = null;
    this.busyId = c.id;
    this.service.delete(c.id).subscribe({
      next: () => { this.busyId = null; this.flash(`Turno ${c.shiftName} quitado del horario`); this.load(true); },
      error: err => { this.busyId = null; this.errorMessage = apiError(err, 'No se pudo quitar el turno'); },
    });
  }

  onSaved(schedule: ParkingShiftRate[]): void {
    this.editing = false;
    this.flash('Horario guardado');
    this.schedule = schedule;
    this.changed.emit(schedule);
    this.load(true);
  }

  private flash(message: string): void {
    this.successMessage = message;
    setTimeout(() => (this.successMessage = ''), 4000);
  }
}
