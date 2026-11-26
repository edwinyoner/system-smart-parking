// src/app/views/parking-service/occupancy/occupancy-map.component.ts
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, exhaustMap, map as toValue, merge, of } from 'rxjs';
import {
  CardComponent,
  CardBodyComponent,
  SpinnerComponent,
  AlertComponent,
  ButtonDirective,
  ModalComponent,
  ModalHeaderComponent,
  ModalTitleDirective,
  ModalBodyComponent,
  ModalFooterComponent,
  ButtonCloseDirective
} from '@coreui/angular';

import { OccupancyService } from '../../../core/services/parking-service/occupancy.service';
import { PermissionService } from '../../../core/services/auth-service/permission.service';
import {
  OccupancyParkingMap,
  OccupancyParkingSummary,
  OccupancySpace,
  OccupancyZone,
  SpaceStatusCode
} from '../../../core/models/parking-service/occupancy.model';
import { liveRefresh } from '../../../core/utils/live-refresh.util';
import { apiError, locationStatus, spaceStatus, spaceTypeLabel } from '../../../core/utils/parking.util';
import { normalizeText } from '../../../core/utils/text.util';

/** Cada cuánto se vuelve a consultar (en silencio, sin spinner) */
const REFRESH_MS = 5000;

interface StatusFilter {
  value: SpaceStatusCode;
  label: string;
  icon: string;
}

/**
 * Mapa de ocupación en tiempo real.
 *
 * Arriba, una tarjeta por parqueo con su disponibilidad; al elegir uno se ven todas sus zonas
 * y, en cada zona, sus espacios como cuadros de colores (libre, ocupado, mantenimiento,
 * fuera de servicio). Se actualiza cada 5 s y al volver a la ventana.
 * Clic en la leyenda filtra por estado; clic en una zona la deja sola; clic en un espacio
 * abre su detalle (con la placa y accesos a la estadía si hay permiso).
 */
@Component({
  selector: 'app-occupancy-map',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    CardComponent,
    CardBodyComponent,
    SpinnerComponent,
    AlertComponent,
    ButtonDirective,
    ModalComponent,
    ModalHeaderComponent,
    ModalTitleDirective,
    ModalBodyComponent,
    ModalFooterComponent,
    ButtonCloseDirective
  ],
  templateUrl: './occupancy-map.component.html',
  styleUrls: ['./occupancy-map.component.css']
})
export class OccupancyMapComponent implements OnInit {

  private readonly occupancy = inject(OccupancyService);
  private readonly permissions = inject(PermissionService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly statuses: StatusFilter[] = [
    { value: 'AVAILABLE', label: 'Libre', icon: 'fa-solid fa-circle-check' },
    { value: 'OCCUPIED', label: 'Ocupado', icon: 'fa-solid fa-car-side' },
    { value: 'MAINTENANCE', label: 'Mantenimiento', icon: 'fa-solid fa-screwdriver-wrench' },
    { value: 'OUT_OF_SERVICE', label: 'Fuera de servicio', icon: 'fa-solid fa-ban' }
  ];

  parkings: OccupancyParkingSummary[] = [];
  map: OccupancyParkingMap | null = null;
  selectedParkingId: number | null = null;

  loading = true;
  loadingMap = false;
  errorMessage = '';
  lastUpdate: Date | null = null;
  liveOk = true;

  // Filtros (se aplican en el navegador)
  statusFilter: SpaceStatusCode | null = null;
  zoneFilter: number | null = null;
  search = '';

  // Detalle del espacio elegido
  selectedSpace: OccupancySpace | null = null;
  selectedZone: OccupancyZone | null = null;

  /** Sin occupancy.view-all solo llegan los parqueos propios (regla 8, la aplica el backend) */
  readonly viewAll = this.permissions.hasPermission('occupancy.view-all');
  readonly canViewStay = this.permissions.hasPermission('transactions.view');
  readonly canExit = this.permissions.hasPermission('transactions.exit');
  readonly canManageSpaces = this.permissions.hasPermission('spaces.read');

  readonly locationStatus = locationStatus;
  readonly spaceStatus = spaceStatus;
  readonly spaceTypeLabel = spaceTypeLabel;

  ngOnInit(): void {
    const fromUrl = Number(this.route.snapshot.queryParamMap.get('parkingId'));

    // Primera carga con spinner y luego en vivo, en silencio
    merge(of(false), liveRefresh(REFRESH_MS).pipe(toValue(() => true)))
      .pipe(
        exhaustMap(silent => this.occupancy.listParkings(silent).pipe(catchError(err => {
          this.onError(err, !silent);
          return EMPTY;
        }))),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(parkings => {
        this.parkings = parkings;
        this.loading = false;
        if (this.selectedParkingId == null && parkings.length > 0) {
          const initial = parkings.find(p => p.id === fromUrl) ?? parkings[0];
          this.selectParking(initial.id);
        } else if (this.selectedParkingId != null && !parkings.some(p => p.id === this.selectedParkingId)) {
          // El parqueo elegido se eliminó mientras se miraba
          this.map = null;
          this.selectedParkingId = null;
        }
      });

    liveRefresh(REFRESH_MS)
      .pipe(
        exhaustMap(() => this.selectedParkingId == null
          ? EMPTY
          : this.occupancy.getParkingMap(this.selectedParkingId, true).pipe(catchError(err => {
              this.onError(err, false);
              return EMPTY;
            }))),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(map => this.applyMap(map));
  }

  // ========================= CARGA =========================

  selectParking(parkingId: number): void {
    if (this.selectedParkingId === parkingId && this.map) {
      return;
    }
    this.selectedParkingId = parkingId;
    this.zoneFilter = null;
    this.loadingMap = true;
    this.router.navigate([], { queryParams: { parkingId }, replaceUrl: true });
    this.occupancy.getParkingMap(parkingId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: map => {
          this.loadingMap = false;
          this.applyMap(map);
        },
        error: err => {
          this.loadingMap = false;
          this.onError(err, true);
        }
      });
  }

  private applyMap(map: OccupancyParkingMap): void {
    if (map.id !== this.selectedParkingId) {
      return;   // respuesta tardía de un parqueo que ya no se mira
    }
    this.map = map;
    this.lastUpdate = new Date();
    this.liveOk = true;
    this.errorMessage = '';
    if (this.zoneFilter != null && !map.zones.some(z => z.id === this.zoneFilter)) {
      this.zoneFilter = null;
    }
    // El detalle abierto sigue al estado nuevo del espacio
    if (this.selectedSpace) {
      for (const zone of map.zones) {
        const space = zone.spaces.find(s => s.id === this.selectedSpace!.id);
        if (space) {
          this.selectedSpace = space;
          this.selectedZone = zone;
          return;
        }
      }
      this.closeSpace();
    }
  }

  private onError(err: any, show: boolean): void {
    this.loading = false;
    this.liveOk = false;
    if (show) {
      this.errorMessage = apiError(err, 'No se pudo cargar el mapa de ocupación');
    }
  }

  // ========================= FILTROS =========================

  toggleStatus(status: SpaceStatusCode): void {
    this.statusFilter = this.statusFilter === status ? null : status;
  }

  toggleZone(zoneId: number | null): void {
    this.zoneFilter = this.zoneFilter === zoneId ? null : zoneId;
  }

  get visibleZones(): OccupancyZone[] {
    if (!this.map) return [];
    return this.zoneFilter == null ? this.map.zones : this.map.zones.filter(z => z.id === this.zoneFilter);
  }

  /** Espacios que pasan el filtro de estado y la búsqueda (código o placa, sin tildes ni guion) */
  visibleSpaces(zone: OccupancyZone): OccupancySpace[] {
    const term = normalizeText(this.search).replace(/[\s-]/g, '');
    return zone.spaces.filter(s =>
      (this.statusFilter == null || s.status === this.statusFilter) &&
      (!term ||
        normalizeText(s.code).replace(/[\s-]/g, '').includes(term) ||
        normalizeText(s.plateNumber ?? '').replace(/[\s-]/g, '').includes(term)));
  }

  get hasFilters(): boolean {
    return this.statusFilter != null || this.zoneFilter != null || !!this.search.trim();
  }

  clearFilters(): void {
    this.statusFilter = null;
    this.zoneFilter = null;
    this.search = '';
  }

  countOf(status: SpaceStatusCode): number {
    const c = this.map?.counts;
    if (!c) return 0;
    return { AVAILABLE: c.available, OCCUPIED: c.occupied, MAINTENANCE: c.maintenance, OUT_OF_SERVICE: c.outOfService }[status];
  }

  // ========================= BARRAS =========================

  /** Ancho en % de cada tramo de la barra de una zona o parqueo */
  pct(part: number, total: number): number {
    return total > 0 ? (part * 100) / total : 0;
  }

  summaryLevel(p: OccupancyParkingSummary): 'ok' | 'mid' | 'full' {
    if (p.totalSpaces === 0 || p.availableSpaces === 0) return 'full';
    return p.occupancyPercentage >= 80 ? 'mid' : 'ok';
  }

  // ========================= DETALLE =========================

  openSpace(space: OccupancySpace, zone: OccupancyZone): void {
    this.selectedSpace = space;
    this.selectedZone = zone;
  }

  closeSpace(): void {
    this.selectedSpace = null;
    this.selectedZone = null;
  }

  /** "1 h 25 min" desde la entrada */
  elapsed(since: string | null): string {
    if (!since) return '';
    const minutes = Math.max(0, Math.floor((Date.now() - new Date(since).getTime()) / 60000));
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return h > 0 ? `${h} h ${m} min` : `${m} min`;
  }

  tileTitle(space: OccupancySpace): string {
    const parts = [space.code, spaceStatus(space.status).label];
    if (space.plateNumber) parts.push(space.plateNumber);
    return parts.join(' - ');
  }
}
