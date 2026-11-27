// src/app/views/parking-service/vehicles/routes.ts
import { Routes } from '@angular/router';
import { RoleGuard } from '../../../core/guards/role.guard';

/** Vehículos: solo consulta e historial (se crean solos por placa) */
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./vehicle-list/vehicle-list.component').then(m => m.VehicleListComponent),
    canActivate: [RoleGuard],
    data: { title: 'Vehículos', requiredPermissions: ['vehicles.read'] }
  },
  {
    path: ':id',
    loadComponent: () => import('./vehicle-detail/vehicle-detail.component').then(m => m.VehicleDetailComponent),
    canActivate: [RoleGuard],
    data: { title: 'Detalle de vehículo', requiredPermissions: ['vehicles.read'] }
  }
];
