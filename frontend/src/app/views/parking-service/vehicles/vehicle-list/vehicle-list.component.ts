// src/app/views/parking-service/vehicles/vehicle-list/vehicle-list.component.ts
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Subject, catchError, debounceTime, exhaustMap, merge, switchMap } from 'rxjs';
import {
  AlertComponent, BadgeComponent, ButtonDirective, CardBodyComponent, CardComponent, CardHeaderComponent, SpinnerComponent
} from '@coreui/angular';

import { VehicleService } from '../../../../core/services/parking-service/vehicle.service';
import { Vehicle } from '../../../../core/models/parking-service/vehicle.model';
import { ParkingPagedResponse } from '../../../../core/models/pagination.model';
import { liveRefresh } from '../../../../core/utils/live-refresh.util';
import { apiError } from '../../../../core/utils/parking.util';

const PAGE_SIZE = 20;

/**
 * Vehículos: se registran solos por placa (entrada o infracción). Búsqueda por placa con o sin
 * guion; muestra si está dentro ahora. En vivo cada 10 s.
 */
@Component({
  selector: 'app-vehicle-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, CardComponent, CardHeaderComponent, CardBodyComponent,
    ButtonDirective, SpinnerComponent, AlertComponent, BadgeComponent],
  templateUrl: './vehicle-list.component.html',
  styleUrls: ['./vehicle-list.component.css']
})
export class VehicleListComponent implements OnInit {

  private readonly vehicleService = inject(VehicleService);
  private readonly destroyRef = inject(DestroyRef);

  search = '';
  page = 0;
  result: ParkingPagedResponse<Vehicle> | null = null;
  loading = true;
  errorMessage = '';
  lastUpdate: Date | null = null;

  private readonly reload$ = new Subject<boolean>();
  private readonly search$ = new Subject<void>();

  ngOnInit(): void {
    this.search$.pipe(debounceTime(350), takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.page = 0;
      this.reload$.next(false);
    });
    merge(
      this.reload$.pipe(switchMap(silent => this.fetch(silent))),
      liveRefresh(10000).pipe(exhaustMap(() => this.fetch(true)))
    ).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(r => {
      this.result = r;
      this.loading = false;
      this.lastUpdate = new Date();
    });
    this.reload$.next(false);
  }

  private fetch(silent: boolean) {
    return this.vehicleService.list(this.page, PAGE_SIZE, this.search, silent).pipe(catchError(err => {
      this.loading = false;
      if (!silent) this.errorMessage = apiError(err, 'No se pudieron cargar los vehículos');
      return EMPTY;
    }));
  }

  onSearch(): void {
    this.search = this.search.toUpperCase();
    this.search$.next();
  }

  goTo(page: number): void {
    if (!this.result || page < 0 || page >= this.result.totalPages) return;
    this.page = page;
    this.reload$.next(false);
  }

  get pages(): number[] {
    const total = this.result?.totalPages ?? 0;
    const from = Math.max(0, Math.min(this.page - 2, total - 5));
    return Array.from({ length: Math.min(5, total) }, (_, i) => from + i);
  }
}
