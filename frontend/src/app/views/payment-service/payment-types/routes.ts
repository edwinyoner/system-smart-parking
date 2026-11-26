import { Routes } from '@angular/router';
import { RoleGuard } from '../../../core/guards/role.guard';

// Cada ruta exige el permiso del rol activo, igual que el backend (@PreAuthorize)
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./payment-type-list/payment-type-list.component').then(m => m.PaymentTypeListComponent),
    canActivate: [RoleGuard],
    data: { title: 'Lista de Tipos de Pago', requiredPermissions: ['payment-types.read'] }
  },
  {
    path: 'create',
    loadComponent: () => import('./payment-type-create/payment-type-create.component').then(m => m.PaymentTypeCreateComponent),
    canActivate: [RoleGuard],
    data: { title: 'Crear Tipo de Pago', requiredPermissions: ['payment-types.create'] }
  },
  {
    path: ':id',
    loadComponent: () => import('./payment-type-detail/payment-type-detail.component').then(m => m.PaymentTypeDetailComponent),
    canActivate: [RoleGuard],
    data: { title: 'Detalle de Tipo de Pago', requiredPermissions: ['payment-types.read'] }
  },
  {
    path: ':id/edit',
    loadComponent: () => import('./payment-type-edit/payment-type-edit.component').then(m => m.PaymentTypeEditComponent),
    canActivate: [RoleGuard],
    data: { title: 'Editar Tipo de Pago', requiredPermissions: ['payment-types.update'] }
  }
];
