import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';

import {
  AvatarComponent,
  BreadcrumbRouterComponent,
  ColorModeService,
  ContainerComponent,
  DropdownComponent,
  DropdownDividerDirective,
  DropdownItemDirective,
  DropdownMenuDirective,
  DropdownToggleDirective,
  HeaderComponent,
  HeaderNavComponent,
  HeaderTogglerDirective,
  NavLinkDirective,
  SidebarToggleDirective,
  BadgeComponent
} from '@coreui/angular';

import { IconDirective } from '@coreui/icons-angular';
import { AuthService } from '../../../core/services/auth.service';
import { CurrentUserService } from '../../../core/services/current-user.service';
import { AuthContextService } from '../../../core/services/auth-context.service';

@Component({
  selector: 'app-default-header',
  templateUrl: './default-header.component.html',
  styleUrls: ['./default-header.component.scss'],
  imports: [
    ContainerComponent,
    HeaderTogglerDirective,
    SidebarToggleDirective,
    IconDirective,
    HeaderNavComponent,
    NavLinkDirective,
    NgTemplateOutlet,
    BreadcrumbRouterComponent,
    DropdownComponent,
    DropdownToggleDirective,
    AvatarComponent,
    DropdownMenuDirective,
    DropdownItemDirective,
    DropdownDividerDirective,
    BadgeComponent
  ]
})
export class DefaultHeaderComponent extends HeaderComponent {

  readonly #colorModeService = inject(ColorModeService);
  readonly colorMode = this.#colorModeService.colorMode;
  
  private authService = inject(AuthService);
  private authContext = inject(AuthContextService);
  private router = inject(Router);

  // Datos del usuario: se recalculan solos cuando cambia CurrentUserService
  private currentUser = toSignal(inject(CurrentUserService).currentUser$, { initialValue: null });

  fullName = computed(() => {
    const user = this.currentUser();
    return `${user?.firstName ?? ''} ${user?.lastName ?? ''}`.trim() || 'Usuario';
  });
  userEmail = computed(() => this.currentUser()?.email || '');
  userInitials = computed(() => {
    const user = this.currentUser();
    const initials = `${user?.firstName?.charAt(0) ?? ''}${user?.lastName?.charAt(0) ?? ''}`;
    return initials.toUpperCase() || '??';
  });
  profilePicture = computed(() => this.currentUser()?.profilePicture || '');
  activeRole = computed(() => {
    this.currentUser(); // El rol activo viene del token: releerlo al cambiar la sesión
    return this.authContext.getActiveRole() || 'Sin rol';
  });
  roleBadgeColor = computed(() => {
    const colorMap: { [key: string]: string } = {
      'ADMIN': 'danger',
      'AUTORIDAD': 'warning',
      'OPERADOR': 'info'
    };
    return colorMap[this.activeRole()] || 'secondary';
  });

  readonly colorModes = [
    { name: 'light', text: 'Light', icon: 'cilSun' },
    { name: 'dark', text: 'Dark', icon: 'cilMoon' },
    { name: 'auto', text: 'Auto', icon: 'cilContrast' }
  ];

  readonly icons = computed(() => {
    const currentMode = this.colorMode();
    return this.colorModes.find(mode => mode.name === currentMode)?.icon ?? 'cilSun';
  });

  constructor() {
    super();
  }

  sidebarId = input('sidebar1');

  /**
   * Cierra sesión del usuario
   */
  logout(): void {
    this.authService.logout();
  }

  /**
   * Navega al perfil del usuario
   */
  goToProfile(): void {
    this.router.navigate(['/profile']);
  }

  /**
   * Navega a editar perfil
   */
  goToEditProfile(): void {
    this.router.navigate(['/profile/edit']);
  }

  /**
   * Navega a cambiar contraseña
   */
  goToChangePassword(): void {
    this.router.navigate(['/profile/change-password']);
  }
}