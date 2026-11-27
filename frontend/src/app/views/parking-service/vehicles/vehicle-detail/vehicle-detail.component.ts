// src/app/views/parking-service/vehicles/vehicle-detail/vehicle-detail.component.ts
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, exhaustMap, filter } from 'rxjs';
import {
  AlertComponent, ButtonDirective, CardBodyComponent, CardComponent, CardHeaderComponent, SpinnerComponent
} from '@coreui/angular';

import { VehicleService } from '../../../../core/services/parking-service/vehicle.service';
import { PermissionService } from '../../../../core/services/auth-service/permission.service';
import { VehicleDetail } from '../../../../core/models/parking-service/vehicle.model';
import { liveRefresh } from '../../../../core/utils/live-refresh.util';
import { apiError } from '../../../../core/utils/parking.util';
import { HistoryPanelComponent } from '../../shared/history-panel/history-panel.component';

/**
 * Vehículo: si está dentro ahora, sus conductores (los que entraron con documento), totales e
 * historial de estadías e infracciones. En vivo cada 10 s.
 */
@Component({
  selector: 'app-vehicle-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, CardComponent, CardHeaderComponent, CardBodyComponent, ButtonDirective,
    SpinnerComponent, AlertComponent, HistoryPanelComponent],
  templateUrl: './vehicle-detail.component.html',
  styleUrls: ['./vehicle-detail.component.css']
})
export class VehicleDetailComponent implements OnInit {

  private readonly route = inject(ActivatedRoute);
  private readonly vehicleService = inject(VehicleService);
  private readonly permissions = inject(PermissionService);
  private readonly destroyRef = inject(DestroyRef);

  readonly canSeeInfractions = this.permissions.hasPermission('infractions.read');
  readonly canSeeCustomers = this.permissions.hasPermission('customers.read');
  readonly canCreateInfraction = this.permissions.hasPermission('infractions.create');
  readonly canExit = this.permissions.hasPermission('transactions.exit');

  id = 0;
  detail: VehicleDetail | null = null;
  loading = true;
  errorMessage = '';

  readonly loadStays = (page: number, silent: boolean) => this.vehicleService.getStays(this.id, page, 10, silent);
  readonly loadInfractions = (page: number, silent: boolean) => this.vehicleService.getInfractions(this.id, page, 10, silent);

  ngOnInit(): void {
    this.id = Number(this.route.snapshot.paramMap.get('id'));
    this.vehicleService.getById(this.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: d => {
        this.detail = d;
        this.loading = false;
      },
      error: err => {
        this.errorMessage = apiError(err, 'No se pudo cargar el vehículo');
        this.loading = false;
      }
    });

    liveRefresh(10000).pipe(
      filter(() => !!this.detail),
      exhaustMap(() => this.vehicleService.getById(this.id, true).pipe(catchError(() => EMPTY))),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(d => (this.detail = d));
  }
}
