import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { Router, ActivatedRoute } from '@angular/router';
import { catchError, forkJoin, of, switchMap, tap } from 'rxjs';

// Services
import { PermissionService } from '../../../../core/services/auth-service/permission.service';
import { RoleService } from '../../../../core/services/auth-service/role.service';
import { AuthContextService } from '../../../../core/services/auth-service/auth-context.service';

// Models
import { Permission } from '../../../../core/models/auth-service/permission.model';
import { Role } from '../../../../core/models/auth-service/role.model';

// Utils
import {
  ActionKind, getActionKind, getModuleInfo, getPermissionActionLabel,
  getPermissionModule, groupPermissionsByModule
} from '../../../../core/utils/permission-module.util';
import { sortRolesByRank, getRoleBadgeClasses, getRoleLabel } from '../../../../core/utils/role.util';

// Components
import { AlertMessageComponent } from '../../../../shared/components/alert-message/alert-message.component';

/**
 * Detalle de un permiso (solo lectura): qué permite, qué roles lo tienen
 * y qué otros permisos hay en su módulo.
 */
@Component({
  selector: 'app-permission-detail',
  standalone: true,
  imports: [CommonModule, AlertMessageComponent],
  templateUrl: './permission-detail.component.html',
  styleUrls: ['./permission-detail.component.css']
})
export class PermissionDetailComponent implements OnInit {

  permission: Permission | null = null;

  /** Roles activos que tienen y que no tienen este permiso */
  rolesWith: Role[] = [];
  rolesWithout: Role[] = [];
  rolesAvailable = false;

  /** Los demás permisos del mismo módulo */
  siblings: Permission[] = [];

  isLoading = false;
  errorMessage: string | null = null;

  private destroyRef = inject(DestroyRef);

  constructor(
    private permissionService: PermissionService,
    private roleService: RoleService,
    private authContext: AuthContextService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    // Se escucha la ruta: al pasar a otro permiso del módulo, Angular reutiliza este componente
    this.route.paramMap.pipe(
      tap(() => {
        this.isLoading = true;
        this.errorMessage = null;
      }),
      switchMap(params => forkJoin({
        permission: this.permissionService.getPermissionById(Number(params.get('id'))),
        all: this.permissionService.getAllPermissions(true).pipe(catchError(() => of([] as Permission[]))),
        roles: this.roleService.getAllRoles(true).pipe(catchError(() => of(null))),
      }).pipe(catchError(() => {
        this.errorMessage = 'No se encontró el permiso o no se pudo cargar';
        return of(null);
      }))),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(data => {
      this.isLoading = false;
      if (!data) {
        this.permission = null;
        return;
      }
      this.setData(data.permission, data.all, data.roles);
    });
  }

  private setData(permission: Permission, all: Permission[], roles: Role[] | null): void {
    this.permission = permission;

    const module = getPermissionModule(permission);
    const group = groupPermissionsByModule(all).find(g => g.module === module);
    this.siblings = (group?.permissions ?? []).filter(p => p.id !== permission.id);

    this.rolesAvailable = roles !== null;
    const sorted = sortRolesByRank(roles ?? []);
    const has = (role: Role) => (role.permissions ?? []).some(p => p.id === permission.id);
    this.rolesWith = sorted.filter(has);
    this.rolesWithout = sorted.filter(role => !has(role));
  }


  hasPermission(permission: string): boolean {
    return this.authContext.hasPermission(permission);
  }

  /** Solo quien entró como ADMIN cambia los permisos de un rol */
  get canEditRoles(): boolean {
    return this.authContext.isActiveRole('ADMIN') && this.hasPermission('roles.update');
  }

  // ========== DATOS PARA LA VISTA ==========

  get moduleInfo() {
    return getModuleInfo(this.permission ? getPermissionModule(this.permission) : '');
  }

  actionLabel(permission: Permission): string {
    return getPermissionActionLabel(permission);
  }

  actionKind(permission: Permission): ActionKind {
    return getActionKind(permission);
  }

  roleLabel(name: string): string {
    return getRoleLabel(name);
  }

  roleBadge(name: string): string {
    return getRoleBadgeClasses(name);
  }

  formatDate(date: string | null | undefined): string {
    if (!date) return '—';
    return new Date(date).toLocaleDateString('es-PE', {
      year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
    });
  }

  // ========== NAVEGACIÓN ==========

  viewPermission(id: number): void {
    this.router.navigate(['/permissions', id]);
  }

  viewRole(role: Role): void {
    this.router.navigate(['/roles', role.id]);
  }

  /** ADMIN no cambia permisos: tiene todos siempre */
  editRole(role: Role): void {
    this.router.navigate(['/roles', role.id, 'edit']);
  }

  goBack(): void {
    this.router.navigate(['/permissions']);
  }

  dismissError(): void {
    this.errorMessage = null;
  }
}
