import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { EMPTY, Observable, catchError, debounceTime, exhaustMap, forkJoin, merge, of } from 'rxjs';

// Services
import { PermissionService } from '../../../core/services/permission.service';
import { RoleService } from '../../../core/services/role.service';
import { AuthContextService } from '../../../core/services/auth-context.service';

// Models
import { Permission } from '../../../core/models/permission.model';
import { Role } from '../../../core/models/role.model';

// Utils
import {
  ACTION_KIND_OPTIONS, ActionKind, getActionKind, getModuleInfo,
  getPermissionActionLabel, getPermissionModule, groupPermissionsByModule
} from '../../../core/utils/permission-module.util';
import { sortRolesByRank, getRoleBadgeClasses, getRoleLabel } from '../../../core/utils/role.util';
import { normalizeText } from '../../../core/utils/text.util';
import { liveRefresh } from '../../../core/utils/live-refresh.util';

// Components
import { AlertMessageComponent } from '../../../shared/components/alert-message/alert-message.component';

/** Un módulo con sus permisos ya filtrados */
interface ModuleView {
  module: string;
  label: string;
  icon: string;
  permissions: Permission[];
  total: number;                 // Permisos del módulo sin filtrar
}

/** Una sección (Seguridad, Estacionamientos...) con sus módulos */
interface SectionView {
  section: string;
  modules: ModuleView[];
}

/** Valor del filtro de rol para "permisos que ningún rol tiene" */
const NO_ROLE = '__none__';

/**
 * Catálogo de permisos (solo lectura).
 *
 * Los permisos nacen en el código (DataSeeder) y no se crean ni editan aquí.
 * Esta vista responde a "¿qué permite cada rol?": muestra los permisos
 * agrupados por módulo y, por cada rol activo, si lo tiene o no (matriz).
 * Son pocos (~60), así que se cargan todos y se filtran en el navegador.
 */
@Component({
  selector: 'app-permission-list',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, AlertMessageComponent],
  templateUrl: './permission-list.component.html',
  styleUrls: ['./permission-list.component.css']
})
export class PermissionListComponent implements OnInit {

  readonly NO_ROLE = NO_ROLE;
  readonly actionOptions = ACTION_KIND_OPTIONS;

  filterForm!: FormGroup;

  /** Todos los permisos y roles, tal como llegan del backend */
  private permissions: Permission[] = [];
  roles: Role[] = [];

  /** Roles que tiene cada permiso (id del permiso → nombres de rol) */
  private rolesByPermission = new Map<number, Set<string>>();

  /** Resultado de aplicar los filtros */
  sections: SectionView[] = [];
  shownCount = 0;

  /** Opciones del filtro de módulo, agrupadas por sección */
  moduleOptions: { section: string; modules: { value: string; label: string }[] }[] = [];

  isLoading = false;
  loaded = false;
  errorMessage: string | null = null;

  /** false si no se pudieron cargar los roles: la vista sigue sin la matriz */
  rolesAvailable = false;

  private destroyRef = inject(DestroyRef);

  constructor(
    private fb: FormBuilder,
    private permissionService: PermissionService,
    private roleService: RoleService,
    private authContext: AuthContextService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.filterForm = this.fb.group({ search: [''], module: [''], action: [''], role: [''] });
    this.setupFilterListeners();
    this.load();
    this.startLiveRefresh();
  }

  hasPermission(permission: string): boolean {
    return this.authContext.hasPermission(permission);
  }

  // ========== CARGA ==========

  load(): void {
    this.isLoading = true;
    this.errorMessage = null;

    this.fetch(false).subscribe({
      next: data => {
        this.isLoading = false;
        this.setData(data.permissions, data.roles);
      },
      error: () => {
        this.isLoading = false;
        this.loaded = true;
        this.errorMessage = 'No se pudieron cargar los permisos del sistema';
      }
    });
  }

  /**
   * Permisos y roles a la vez. Si fallan los roles (por ejemplo, sin permiso para verlos),
   * se muestran igual los permisos, sin la matriz.
   */
  private fetch(silent: boolean): Observable<{ permissions: Permission[]; roles: Role[] | null }> {
    return forkJoin({
      permissions: this.permissionService.getAllPermissions(silent),
      roles: this.roleService.getAllRoles(silent).pipe(catchError(() => of(null))),
    });
  }

  /** Mantiene la matriz al día si alguien cambia los permisos de un rol */
  private startLiveRefresh(): void {
    liveRefresh(10000).pipe(
      exhaustMap(() => this.fetch(true).pipe(catchError(() => EMPTY))),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(data => this.setData(data.permissions, data.roles));
  }

  private setData(permissions: Permission[], roles: Role[] | null): void {
    this.loaded = true;
    this.permissions = permissions;
    this.rolesAvailable = roles !== null;
    this.roles = sortRolesByRank(roles ?? []);

    this.rolesByPermission = new Map();
    for (const role of this.roles) {
      for (const permission of role.permissions ?? []) {
        const names = this.rolesByPermission.get(permission.id) ?? new Set<string>();
        names.add(role.name);
        this.rolesByPermission.set(permission.id, names);
      }
    }

    this.buildModuleOptions();

    // El rol elegido en el filtro pudo desactivarse o eliminarse
    const role = this.filterForm.get('role');
    if (role?.value && role.value !== NO_ROLE && !this.roles.some(r => r.name === role.value)) {
      role.setValue('', { emitEvent: false });
    }

    this.applyFilters();
  }


  private buildModuleOptions(): void {
    const options: typeof this.moduleOptions = [];
    for (const group of groupPermissionsByModule(this.permissions)) {
      let section = options.find(o => o.section === group.section);
      if (!section) {
        section = { section: group.section!, modules: [] };
        options.push(section);
      }
      section.modules.push({ value: group.module, label: group.label! });
    }
    this.moduleOptions = options;
  }

  // ========== FILTROS ==========

  private setupFilterListeners(): void {
    const { search, module, action, role } = this.filterForm.controls;
    merge(
      search.valueChanges.pipe(debounceTime(250)),
      module.valueChanges,
      action.valueChanges,
      role.valueChanges
    ).pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.applyFilters());
  }

  /** Filtra en el navegador y arma las secciones a mostrar */
  applyFilters(): void {
    // No usar filterForm.value: dentro de valueChanges aún tendría el valor anterior
    const { search, module, action, role } = this.filterForm.getRawValue();
    const words = normalizeText(search).split(/\s+/).filter(Boolean);

    const matches = (permission: Permission): boolean => {
      if (module && getPermissionModule(permission) !== module) return false;
      if (action && getActionKind(permission) !== (action as ActionKind)) return false;
      if (role) {
        const owners = this.rolesByPermission.get(permission.id);
        if (role === NO_ROLE ? owners?.size : !owners?.has(role)) return false;
      }
      if (words.length) {
        // Se busca en el código, la descripción y los nombres en español del módulo y la acción
        const text = normalizeText([
          permission.name,
          permission.description,
          getModuleInfo(getPermissionModule(permission)).label,
          getPermissionActionLabel(permission),
        ].join(' '));
        return words.every(word => text.includes(word));
      }
      return true;
    };

    const sections: SectionView[] = [];
    let shown = 0;
    for (const group of groupPermissionsByModule(this.permissions)) {
      const filtered = group.permissions.filter(matches);
      if (!filtered.length) continue;
      shown += filtered.length;

      let section = sections.find(s => s.section === group.section);
      if (!section) {
        section = { section: group.section!, modules: [] };
        sections.push(section);
      }
      section.modules.push({
        module: group.module,
        label: group.label!,
        icon: group.icon!,
        permissions: filtered,
        total: group.permissions.length,
      });
    }

    this.sections = sections;
    this.shownCount = shown;
  }

  get activeFilterCount(): number {
    return Object.values(this.filterForm.getRawValue()).filter(value => !!String(value).trim()).length;
  }

  clearSearch(): void {
    this.filterForm.patchValue({ search: '' });
  }

  clearFilters(): void {
    // Sin emitir eventos: se filtra una sola vez
    this.filterForm.reset({ search: '', module: '', action: '', role: '' }, { emitEvent: false });
    this.applyFilters();
  }

  /** Filtra por un rol desde la cabecera de la matriz (un segundo clic lo quita) */
  toggleRoleFilter(roleName: string): void {
    const control = this.filterForm.get('role')!;
    control.setValue(control.value === roleName ? '' : roleName);
  }

  // ========== DATOS PARA LA VISTA ==========

  get totalCount(): number {
    return this.permissions.length;
  }

  get moduleCount(): number {
    return this.moduleOptions.reduce((count, section) => count + section.modules.length, 0);
  }

  hasRole(permission: Permission, roleName: string): boolean {
    return this.rolesByPermission.get(permission.id)?.has(roleName) ?? false;
  }

  roleCount(permission: Permission): number {
    return this.rolesByPermission.get(permission.id)?.size ?? 0;
  }

  /** Cuántos permisos del módulo (de los mostrados) tiene un rol */
  moduleRoleCount(module: ModuleView, roleName: string): number {
    return module.permissions.filter(p => this.hasRole(p, roleName)).length;
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

  /** Columnas de la tabla: acción, código, descripción, una por rol y ver */
  get columnCount(): number {
    return 4 + (this.rolesAvailable ? this.roles.length : 0);
  }

  // ========== NAVEGACIÓN ==========

  viewPermission(id: number): void {
    this.router.navigate(['/permissions', id]);
  }

  goToRoles(): void {
    this.router.navigate(['/roles']);
  }

  dismissError(): void {
    this.errorMessage = null;
  }
}
