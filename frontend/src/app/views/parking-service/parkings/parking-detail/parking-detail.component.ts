import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EMPTY, catchError, exhaustMap, forkJoin } from 'rxjs';

import { ParkingService } from '../../../../core/services/parking-service/parking.service';
import { ZoneService } from '../../../../core/services/parking-service/zone.service';
import { AuthContextService } from '../../../../core/services/auth-service/auth-context.service';
import { Parking } from '../../../../core/models/parking-service/parking.model';
import { Zone } from '../../../../core/models/parking-service/zone.model';
import { ParkingShiftRate } from '../../../../core/models/parking-service/parking-shift-rate.model';
import { AlertMessageComponent } from '../../../../shared/components/alert-message/alert-message.component';
import { takeSuccessMessage } from '../../../../core/utils/flash-message.util';
import { liveRefresh } from '../../../../core/utils/live-refresh.util';
import { apiError, locationStatus } from '../../../../core/utils/parking.util';
import { ParkingScheduleComponent } from './parking-schedule/parking-schedule.component';
import { ParkingOperatorsComponent } from './parking-operators/parking-operators.component';

/**
 * Detalle del parqueo: datos, capacidad en vivo, zonas (con papelera), horario y
 * operadores. Es la puerta de entrada a la gestión de zonas y espacios.
 */
@Component({
  selector: 'app-parking-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, AlertMessageComponent, ParkingScheduleComponent, ParkingOperatorsComponent],
  templateUrl: './parking-detail.component.html',
  styleUrls: ['./parking-detail.component.css'],
})
export class ParkingDetailComponent implements OnInit {

  parkingId!: number;
  parking: Parking | null = null;
  zones: Zone[] = [];
  schedule: ParkingShiftRate[] = [];

  loading = true;
  errorMessage: string | null = null;
  successMessage: string | null = null;

  showDeletedZones = false;
  zoneToDelete: Zone | null = null;
  zoneToRestore: Zone | null = null;
  showDeleteParking = false;

  readonly status = locationStatus;
  private destroyRef = inject(DestroyRef);

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private parkingService: ParkingService,
    private zoneService: ZoneService,
    private authContext: AuthContextService,
  ) {
    this.successMessage = takeSuccessMessage();
  }

  ngOnInit(): void {
    this.parkingId = Number(this.route.snapshot.paramMap.get('id'));
    this.load(false);
    liveRefresh(5000).pipe(
      exhaustMap(() => this.request(true).pipe(catchError(() => EMPTY))),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(r => this.apply(r));
  }

  private request(silent: boolean) {
    return forkJoin({
      parking: this.parkingService.getById(this.parkingId, silent),
      zones: this.showDeletedZones
        ? this.zoneService.getAll(0, 100, '', '', this.parkingId, true, silent)
        : this.zoneService.getByParkingId(this.parkingId, silent),
    });
  }

  private apply(r: { parking: Parking; zones: Zone[] | { content: Zone[] } }): void {
    this.parking = r.parking;
    this.zones = Array.isArray(r.zones) ? r.zones : r.zones.content;
  }

  load(silent: boolean): void {
    this.request(silent).subscribe({
      next: r => { this.loading = false; this.apply(r); },
      error: err => { this.loading = false; this.errorMessage = apiError(err, 'No se pudo cargar el parqueo'); },
    });
  }

  can(permission: string): boolean {
    return this.authContext.hasPermission(permission);
  }

  /** Zonas vigentes para el bloque de operadores (en la papelera no se asigna) */
  get activeZones(): Zone[] {
    return this.showDeletedZones ? [] : this.zones;
  }

  setShowDeletedZones(deleted: boolean): void {
    if (this.showDeletedZones === deleted) return;
    this.showDeletedZones = deleted;
    this.zones = [];
    this.load(false);
  }

  occupancyClass(pct?: number): string {
    if ((pct ?? 0) >= 90) return 'bg-danger';
    if ((pct ?? 0) >= 70) return 'bg-warning';
    return 'bg-success';
  }

  // ========================= ZONAS =========================

  createZone(): void {
    this.router.navigate(['/zones/create'], { queryParams: { parkingId: this.parkingId } });
  }

  deleteZone(): void {
    const zone = this.zoneToDelete;
    if (!zone?.id) return;
    this.zoneToDelete = null;
    this.zoneService.delete(zone.id).subscribe({
      next: () => { this.successMessage = `Zona "${zone.name}" eliminada`; this.load(true); },
      error: err => (this.errorMessage = apiError(err, 'No se pudo eliminar la zona')),
    });
  }

  restoreZone(): void {
    const zone = this.zoneToRestore;
    if (!zone?.id) return;
    this.zoneToRestore = null;
    this.zoneService.restore(zone.id).subscribe({
      next: () => { this.successMessage = `Zona "${zone.name}" restaurada con sus espacios`; this.load(true); },
      error: err => (this.errorMessage = apiError(err, 'No se pudo restaurar la zona')),
    });
  }

  // ========================= PARQUEO =========================

  deleteParking(): void {
    this.showDeleteParking = false;
    this.parkingService.delete(this.parkingId).subscribe({
      next: () => this.router.navigate(['/parkings'],
        { state: { successMessage: `Parqueo "${this.parking?.name}" eliminado. Puedes restaurarlo desde Eliminados.` } }),
      error: err => (this.errorMessage = apiError(err, 'No se pudo eliminar el parqueo')),
    });
  }

  onScheduleChanged(schedule: ParkingShiftRate[]): void {
    this.schedule = schedule;
  }
}
