import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRouteSnapshot, Router, RouterLink, RouterOutlet } from '@angular/router';
import { NgScrollbar } from 'ngx-scrollbar';
import { CommonModule } from '@angular/common';

import {
  ContainerComponent,
  ShadowOnScrollDirective,
  SidebarBrandComponent,
  SidebarComponent,
  SidebarFooterComponent,
  SidebarHeaderComponent,
  SidebarNavComponent,
  SidebarTogglerDirective
} from '@coreui/angular';

import { DefaultFooterComponent, DefaultHeaderComponent } from './';
import { navItems, INavDataWithPermissions } from './_nav';
import { CurrentUserService } from '../../core/services/auth-service/current-user.service';
import { SessionSyncService } from '../../core/services/auth-service/session-sync.service';
import { AuthContextService } from '../../core/services/auth-service/auth-context.service';
import { PermissionService } from '../../core/services/auth-service/permission.service';

@Component({
  selector: 'app-dashboard',
  templateUrl: './default-layout.component.html',
  styleUrls: ['./default-layout.component.css'],
  imports: [
    CommonModule,
    SidebarComponent,
    SidebarHeaderComponent,
    SidebarBrandComponent,
    SidebarNavComponent,
    SidebarFooterComponent,
    SidebarTogglerDirective,
    ContainerComponent,
    DefaultFooterComponent,
    DefaultHeaderComponent,
    NgScrollbar,
    RouterOutlet,
    RouterLink,
    ShadowOnScrollDirective
  ]
})
export class DefaultLayoutComponent implements OnInit {
  
  public navItems: INavDataWithPermissions[] = [];

  // Menú minimizado (solo íconos y foto). Se recuerda entre sesiones en este navegador
  private static readonly COLLAPSED_KEY = 'sp-sidebar-collapsed';
  public collapsed = signal(localStorage.getItem(DefaultLayoutComponent.COLLAPSED_KEY) === 'true');

  toggleCollapsed(): void {
    this.collapsed.update(value => !value);
    localStorage.setItem(DefaultLayoutComponent.COLLAPSED_KEY, String(this.collapsed()));
  }

  // Datos del usuario: se recalculan solos cuando cambia CurrentUserService
  private currentUserService = inject(CurrentUserService);
  private currentUser = toSignal(this.currentUserService.currentUser$, { initialValue: null });

  public fullName = computed(() => {
    const user = this.currentUser();
    return `${user?.firstName ?? ''} ${user?.lastName ?? ''}`.trim();
  });
  public userInitials = computed(() => {
    const user = this.currentUser();
    return `${user?.firstName?.charAt(0) ?? ''}${user?.lastName?.charAt(0) ?? ''}`.toUpperCase();
  });
  public profilePicture = computed(() => this.currentUser()?.profilePicture || null);
  public activeRole = computed(() => {
    this.currentUser(); // El rol activo viene del token: releerlo al cambiar la sesión
    return this.authContext.getActiveRole() || '';
  });
  public roleBadgeClass = computed(() => {
    this.currentUser();
    return this.authContext.getRoleBadgeClass();
  });

  constructor(
    private authContext: AuthContextService,
    private permissionService: PermissionService,
    sessionSync: SessionSyncService
  ) {
    // Activa solo mientras el dashboard está abierto
    sessionSync.sync().pipe(takeUntilDestroyed()).subscribe();

    // Si un administrador cambia los permisos del rol, el menú se actualiza sin volver a entrar
    // y, si la página abierta ya no está permitida, se sale de ella
    this.currentUserService.currentUser$.pipe(takeUntilDestroyed()).subscribe(user => {
      const key = (user?.permissions ?? []).slice().sort().join(',');
      if (key === this.permissionsKey) {
        return;
      }
      const changed = this.permissionsKey !== null;
      this.permissionsKey = key;
      this.filterNavItems();
      if (changed) {
        this.leaveIfNotAllowed();
      }
    });
  }

  ngOnInit(): void {
    this.filterNavItems();
  }

  /** Permisos con los que se filtró el menú por última vez (null antes de la primera vez) */
  private permissionsKey: string | null = null;
  private router = inject(Router);

  /**
   * Misma comprobación que RoleGuard sobre la página abierta: si ya no se tiene
   * el permiso que pide su ruta (data.requiredPermissions), se vuelve al dashboard.
   */
  private leaveIfNotAllowed(): void {
    if (this.authContext.isActiveRole('ADMIN')) {
      return;
    }
    let route: ActivatedRouteSnapshot | null = this.router.routerState.snapshot.root;
    while (route) {
      const required = route.data?.['requiredPermissions'] as string[] | undefined;
      const requireAll = route.data?.['requireAllPermissions'] === true;
      if (required?.length) {
        const allowed = requireAll
          ? this.permissionService.hasAllPermissions(required)
          : this.permissionService.hasAnyPermission(required);
        if (!allowed) {
          this.router.navigate(['/dashboard'], {
            queryParams: { error: 'unauthorized', message: 'Ya no tienes permiso para esta página' }
          });
          return;
        }
      }
      route = route.firstChild;
    }
  }

  /**
   * Filtra el menú según los PERMISOS del usuario
   */
  private filterNavItems(): void {
    const visible = navItems.filter(item => {
      // Si no tiene permisos requeridos, mostrar a todos
      if (!item.requiredPermissions || item.requiredPermissions.length === 0) {
        return true;
      }

      // Verificar si tiene AL MENOS UNO de los permisos
      return this.permissionService.hasAnyPermission(item.requiredPermissions);
    });

    // Un título de sección sin opciones visibles debajo se oculta
    this.navItems = visible.filter((item, index) => {
      if (!item.title) {
        return true;
      }
      const next = visible[index + 1];
      return !!next && !next.title;
    });
  }
}