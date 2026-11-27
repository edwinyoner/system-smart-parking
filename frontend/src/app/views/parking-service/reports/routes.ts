// src/app/views/parking-service/reports/routes.ts
import { Routes } from '@angular/router';
import { RoleGuard } from '../../../core/guards/role.guard';

/** Reportes (permiso reports.view; exportar a CSV y PDF con reports.export) */
export const routes: Routes = [
  {
    path: '',
    redirectTo: 'daily',
    pathMatch: 'full'
  },
  {
    path: 'daily',
    loadComponent: () => import('./daily-report/daily-report.component').then(m => m.DailyReportComponent),
    canActivate: [RoleGuard],
    data: { title: 'Reporte Diario', requiredPermissions: ['reports.view'] }
  },
  {
    path: 'revenue',
    loadComponent: () => import('./revenue-report/revenue-report.component').then(m => m.RevenueReportComponent),
    canActivate: [RoleGuard],
    data: { title: 'Reporte de Ingresos', requiredPermissions: ['reports.view'] }
  },
  {
    path: 'occupancy',
    loadComponent: () => import('./occupancy-report/occupancy-report.component').then(m => m.OccupancyReportComponent),
    canActivate: [RoleGuard],
    data: { title: 'Reporte de Ocupación', requiredPermissions: ['reports.view'] }
  }
];
