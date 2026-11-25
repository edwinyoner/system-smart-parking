import { Routes } from '@angular/router';
import { RoleGuard } from '../../../core/guards/role.guard';

// Cada ruta exige el permiso del rol activo, igual que el backend (@PreAuthorize)
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./rate-list/rate-list.component').then(m => m.RateListComponent),
    canActivate: [RoleGuard],
    data: { title: 'Lista de Tarifas', requiredPermissions: ['rates.read'] }
  },
  {
    path: 'create',
    loadComponent: () => import('./rate-create/rate-create.component').then(m => m.RateCreateComponent),
    canActivate: [RoleGuard],
    data: { title: 'Crear Tarifa', requiredPermissions: ['rates.create'] }
  },
  {
    path: ':id',
    loadComponent: () => import('./rate-detail/rate-detail.component').then(m => m.RateDetailComponent),
    canActivate: [RoleGuard],
    data: { title: 'Detalle de Tarifa', requiredPermissions: ['rates.read'] }
  },
  {
    path: ':id/edit',
    loadComponent: () => import('./rate-edit/rate-edit.component').then(m => m.RateEditComponent),
    canActivate: [RoleGuard],
    data: { title: 'Editar Tarifa', requiredPermissions: ['rates.update'] }
  }
];
