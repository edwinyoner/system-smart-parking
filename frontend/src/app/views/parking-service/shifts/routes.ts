import { Routes } from '@angular/router';
import { RoleGuard } from '../../../core/guards/role.guard';

// Cada ruta exige el permiso del rol activo, igual que el backend (@PreAuthorize)
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./shift-list/shift-list.component').then(m => m.ShiftListComponent),
    canActivate: [RoleGuard],
    data: { title: 'Lista de Turnos', requiredPermissions: ['shifts.read'] }
  },
  {
    path: 'create',
    loadComponent: () => import('./shift-create/shift-create.component').then(m => m.ShiftCreateComponent),
    canActivate: [RoleGuard],
    data: { title: 'Crear Turno', requiredPermissions: ['shifts.create'] }
  },
  {
    path: ':id',
    loadComponent: () => import('./shift-detail/shift-detail.component').then(m => m.ShiftDetailComponent),
    canActivate: [RoleGuard],
    data: { title: 'Detalle de Turno', requiredPermissions: ['shifts.read'] }
  },
  {
    path: ':id/edit',
    loadComponent: () => import('./shift-edit/shift-edit.component').then(m => m.ShiftEditComponent),
    canActivate: [RoleGuard],
    data: { title: 'Editar Turno', requiredPermissions: ['shifts.update'] }
  }
];
