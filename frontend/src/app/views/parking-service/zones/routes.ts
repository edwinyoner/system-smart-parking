// src/app/views/parking-service/zones/routes.ts
import { Routes } from '@angular/router';
import { RoleGuard } from '../../../core/guards/role.guard';

// Las zonas se gestionan desde el detalle de su parqueo; los espacios, desde el de su zona
export const routes: Routes = [
  { path: '', redirectTo: '/parkings', pathMatch: 'full' },
  {
    path: 'create',
    loadComponent: () => import('./zone-form/zone-form.component').then(m => m.ZoneFormComponent),
    canActivate: [RoleGuard],
    data: { title: 'Nueva Zona', requiredPermissions: ['zones.create'] }
  },
  {
    path: ':id',
    loadComponent: () => import('./zone-detail/zone-detail.component').then(m => m.ZoneDetailComponent),
    canActivate: [RoleGuard],
    data: { title: 'Detalle de Zona', requiredPermissions: ['zones.read'] }
  },
  {
    path: ':id/edit',
    loadComponent: () => import('./zone-form/zone-form.component').then(m => m.ZoneFormComponent),
    canActivate: [RoleGuard],
    data: { title: 'Editar Zona', requiredPermissions: ['zones.update'] }
  },
];
