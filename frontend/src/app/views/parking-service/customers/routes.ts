// src/app/views/parking-service/customers/routes.ts
import { Routes } from '@angular/router';
import { RoleGuard } from '../../../core/guards/role.guard';

/** Clientes: solo consulta e historial (se crean solos en la entrada con documento) */
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./customer-list/customer-list.component').then(m => m.CustomerListComponent),
    canActivate: [RoleGuard],
    data: { title: 'Clientes', requiredPermissions: ['customers.read'] }
  },
  {
    path: ':id',
    loadComponent: () => import('./customer-detail/customer-detail.component').then(m => m.CustomerDetailComponent),
    canActivate: [RoleGuard],
    data: { title: 'Detalle de cliente', requiredPermissions: ['customers.read'] }
  }
];
