import { Routes } from '@angular/router';
import { AuthGuard } from '../../core/guards/auth.guard';

// Vistas de payment-service. Cada módulo protege sus rutas por permiso con RoleGuard
export const paymentRoutes: Routes = [
  {
    path: 'payment-types',
    loadChildren: () => import('./payment-types/routes').then((m) => m.routes),
    canActivate: [AuthGuard],
  },
  {
    path: 'payments',
    loadChildren: () => import('./payments/routes').then((m) => m.routes),
    canActivate: [AuthGuard],
  },
];
