import { Routes } from '@angular/router';
import { RoleGuard } from '../../../core/guards/role.guard';

// Cada ruta exige el permiso del rol activo, igual que el backend (@PreAuthorize)
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./document-type-list/document-type-list.component').then(m => m.DocumentTypeListComponent),
    canActivate: [RoleGuard],
    data: { title: 'Lista de Tipos de Documento', requiredPermissions: ['document-types.read'] }
  },
  {
    path: 'create',
    loadComponent: () => import('./document-type-create/document-type-create.component').then(m => m.DocumentTypeCreateComponent),
    canActivate: [RoleGuard],
    data: { title: 'Crear Tipo de Documento', requiredPermissions: ['document-types.create'] }
  },
  {
    path: ':id',
    loadComponent: () => import('./document-type-detail/document-type-detail.component').then(m => m.DocumentTypeDetailComponent),
    canActivate: [RoleGuard],
    data: { title: 'Detalle de Tipo de Documento', requiredPermissions: ['document-types.read'] }
  },
  {
    path: ':id/edit',
    loadComponent: () => import('./document-type-edit/document-type-edit.component').then(m => m.DocumentTypeEditComponent),
    canActivate: [RoleGuard],
    data: { title: 'Editar Tipo de Documento', requiredPermissions: ['document-types.update'] }
  }
];
