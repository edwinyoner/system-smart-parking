import { Component, computed, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink, RouterOutlet } from '@angular/router';
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
  SidebarToggleDirective,
  SidebarTogglerDirective
} from '@coreui/angular';

import { DefaultFooterComponent, DefaultHeaderComponent } from './';
import { navItems, INavDataWithPermissions } from './_nav';
import { CurrentUserService } from '../../core/services/current-user.service';
import { SessionSyncService } from '../../core/services/session-sync.service';
import { AuthContextService } from '../../core/services/auth-context.service';
import { PermissionService } from '../../core/services/permission.service';

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
    SidebarToggleDirective,
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
  }

  ngOnInit(): void {
    this.filterNavItems();
  }

  /**
   * Filtra el menú según los PERMISOS del usuario
   */
  private filterNavItems(): void {
    this.navItems = navItems.filter(item => {
      // Si no tiene permisos requeridos, mostrar a todos
      if (!item.requiredPermissions || item.requiredPermissions.length === 0) {
        return true;
      }

      // Verificar si tiene AL MENOS UNO de los permisos
      return this.permissionService.hasAnyPermission(item.requiredPermissions);
    });
  }
}