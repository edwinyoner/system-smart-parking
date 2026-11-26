import { Routes } from '@angular/router';
import { RoleGuard } from '../../../core/guards/role.guard';

// Cada ruta exige el permiso del rol activo, igual que el backend (@PreAuthorize)
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./parking-list/parking-list.component').then(m => m.ParkingListComponent),
    canActivate: [RoleGuard],
    data: { title: 'Lista de Parqueos', requiredPermissions: ['parkings.read'] }
  },
  {
    path: 'create',
    loadComponent: () => import('./parking-create/parking-create.component').then(m => m.ParkingCreateComponent),
    canActivate: [RoleGuard],
    data: { title: 'Nuevo Parqueo', requiredPermissions: ['parkings.create'] }
  },
  {
    path: ':id',
    loadComponent: () => import('./parking-detail/parking-detail.component').then(m => m.ParkingDetailComponent),
    canActivate: [RoleGuard],
    data: { title: 'Detalle de Parqueo', requiredPermissions: ['parkings.read'] }
  },
  {
    path: ':id/edit',
    loadComponent: () => import('./parking-edit/parking-edit.component').then(m => m.ParkingEditComponent),
    canActivate: [RoleGuard],
    data: { title: 'Editar Parqueo', requiredPermissions: ['parkings.update'] }
  },
];
