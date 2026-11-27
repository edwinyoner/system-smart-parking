// src/app/views/parking-service/infractions/routes.ts
import { Routes } from '@angular/router';
import { RoleGuard } from '../../../core/guards/role.guard';

const form = () => import('./infraction-form/infraction-form.component').then(m => m.InfractionFormComponent);

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./infraction-list/infraction-list.component').then(m => m.InfractionListComponent),
    canActivate: [RoleGuard],
    data: { title: 'Infracciones', requiredPermissions: ['infractions.read'] }
  },
  {
    path: 'create',
    loadComponent: form,
    canActivate: [RoleGuard],
    data: { title: 'Registrar infracción', requiredPermissions: ['infractions.create'] }
  },
  {
    path: ':id',
    loadComponent: () => import('./infraction-detail/infraction-detail.component').then(m => m.InfractionDetailComponent),
    canActivate: [RoleGuard],
    data: { title: 'Detalle de infracción', requiredPermissions: ['infractions.read'] }
  },
  {
    path: ':id/edit',
    loadComponent: form,
    canActivate: [RoleGuard],
    data: { title: 'Editar infracción', requiredPermissions: ['infractions.update'] }
  }
];
