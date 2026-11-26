// src/app/views/parking-service/transactions/routes.ts
import { Routes } from '@angular/router';
import { RoleGuard } from '../../../core/guards/role.guard';

export const routes: Routes = [
  {
    path: '',
    data: { title: 'Transacciones' },
    children: [
      {
        path: '',
        redirectTo: 'list',
        pathMatch: 'full'
      },
      // Vehículos dentro, en vivo
      {
        path: 'active',
        loadComponent: () =>
          import('./active-transactions/active-transactions.component').then(
            (m) => m.ActiveTransactionsComponent
          ),
        canActivate: [RoleGuard],
        data: { title: 'Vehículos dentro', requiredPermissions: ['transactions.view'] }
      },
      // Cola de lecturas dudosas de las cámaras
      {
        path: 'reviews',
        loadComponent: () =>
          import('./plate-reviews/plate-reviews.component').then((m) => m.PlateReviewsComponent),
        canActivate: [RoleGuard],
        data: { title: 'Por revisar', requiredPermissions: ['transactions.view'] }
      },
      // Operaciones
      {
        path: 'entry',
        loadComponent: () =>
          import('./transaction-entry/transaction-entry.component').then(
            (m) => m.TransactionEntryComponent
          ),
        canActivate: [RoleGuard],
        data: { title: 'Registrar Entrada', requiredPermissions: ['transactions.entry'] }
      },
      {
        path: 'exit',
        loadComponent: () =>
          import('./transaction-exit/transaction-exit.component').then(
            (m) => m.TransactionExitComponent
          ),
        canActivate: [RoleGuard],
        data: { title: 'Registrar Salida', requiredPermissions: ['transactions.exit'] }
      },
      {
        path: 'payment',
        loadComponent: () =>
          import('./transaction-payment/transaction-payment.component').then(
            (m) => m.TransactionPaymentComponent
          ),
        canActivate: [RoleGuard],
        data: { title: 'Procesar Pago', requiredPermissions: ['payments.process'] }
      },
      // Historial
      {
        path: 'list',
        loadComponent: () =>
          import('./transaction-list/transaction-list.component').then(
            (m) => m.TransactionListComponent
          ),
        canActivate: [RoleGuard],
        data: { title: 'Historial de Transacciones', requiredPermissions: ['transactions.view'] }
      },
      // Detalle
      {
        path: ':id',
        loadComponent: () =>
          import('./transaction-detail/transaction-detail.component').then(
            (m) => m.TransactionDetailComponent
          ),
        canActivate: [RoleGuard],
        data: { title: 'Detalle de Transacción', requiredPermissions: ['transactions.view'] }
      }
    ]
  }
];