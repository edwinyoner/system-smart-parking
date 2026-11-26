import { Routes } from '@angular/router';
import { RoleGuard } from '../../../core/guards/role.guard';

// Historial de pagos (payment-service). Cobrar se hace desde la estadía o la multa
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./payment-list/payment-list.component').then(m => m.PaymentListComponent),
    canActivate: [RoleGuard],
    data: { title: 'Historial de Pagos', requiredPermissions: ['payments.view'] }
  },
  {
    path: ':id',
    loadComponent: () => import('./payment-detail/payment-detail.component').then(m => m.PaymentDetailComponent),
    canActivate: [RoleGuard],
    data: { title: 'Detalle de Pago', requiredPermissions: ['payments.view'] }
  }
];
