import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { TokenService } from './token.service';
import { UserInfo } from '../../models/auth-service/auth-response.model';

/** Datos personales que pueden cambiar sin renovar la sesión */
export interface CurrentUserProfile {
  firstName: string;
  lastName: string;
  phoneNumber?: string | null;
  profilePicture?: string | null;
  /** Permisos vigentes del rol activo (solo los trae GET /users/me) */
  activePermissions?: string[];
}

/**
 * Servicio para gestionar información del usuario actual
 * Mantiene el estado del usuario logueado en la aplicación
 */
@Injectable({
  providedIn: 'root'
})
export class CurrentUserService {

  private currentUserSubject = new BehaviorSubject<UserInfo | null>(null);
  public currentUser$ = this.currentUserSubject.asObservable();

  constructor(private tokenService: TokenService) {
    this.refreshCurrentUser();
  }

  // ========== USUARIO ACTUAL ==========

  /**
   * Obtiene el usuario actual desde el token
   */
  private getUserFromToken(): UserInfo | null {
    const tokenData = this.tokenService.getUserFromToken();
    
    if (!tokenData) {
      return null;
    }

    // Convertir datos del token a UserInfo
    return {
      id: Number(tokenData.userId), // 'sub' contiene el email, no el id
      email: tokenData.email,
      firstName: tokenData.firstName,
      lastName: tokenData.lastName,
      phoneNumber: tokenData.phoneNumber,
      profilePicture: tokenData.profilePicture,
      emailVerified: tokenData.emailVerified,
      status: tokenData.status,
      roles: tokenData.roles || [],
      permissions: tokenData.permissions || []
    };
  }

  /**
   * Actualiza la información del usuario actual
   */
  setCurrentUser(user: UserInfo | null): void {
    this.currentUserSubject.next(user);
  }

  /**
   * Aplica los datos devueltos por el backend (tras una edición o en la sincronización).
   * Si vienen los permisos vigentes, reemplazan a los del token: así los botones y el menú
   * cambian en cuanto un administrador modifica los permisos del rol, sin volver a entrar.
   */
  applyProfile(profile: CurrentUserProfile): void {
    const user = this.getCurrentUser();
    if (!user) {
      return;
    }

    // El backend envía null cuando el campo se borró
    const phoneNumber = profile.phoneNumber || undefined;
    const profilePicture = profile.profilePicture || undefined;

    const permissions = profile.activePermissions ?? user.permissions;

    // Sin cambios no se emite: evita refrescar la pantalla en cada sincronización
    if (user.firstName === profile.firstName
      && user.lastName === profile.lastName
      && (user.phoneNumber || undefined) === phoneNumber
      && (user.profilePicture || undefined) === profilePicture
      && this.samePermissions(user.permissions, permissions)) {
      return;
    }

    this.setCurrentUser({
      ...user,
      firstName: profile.firstName,
      lastName: profile.lastName,
      phoneNumber,
      profilePicture,
      permissions
    });
  }

  /** Mismos permisos sin importar el orden */
  private samePermissions(a: string[], b: string[]): boolean {
    if (a.length !== b.length) {
      return false;
    }
    const set = new Set(a);
    return b.every(permission => set.has(permission));
  }

  /**
   * Obtiene el usuario actual (snapshot)
   */
  getCurrentUser(): UserInfo | null {
    return this.currentUserSubject.value;
  }

  /**
   * Refresca la información del usuario desde el token
   */
  refreshCurrentUser(): void {
    const user = this.getUserFromToken();
    this.setCurrentUser(user);
  }

  /**
   * Limpia la información del usuario actual
   */
  clearCurrentUser(): void {
    this.currentUserSubject.next(null);
  }

  // ========== VERIFICACIÓN DE ROLES Y PERMISOS ==========

  /**
   * Verifica si el usuario tiene un rol específico
   */
  hasRole(role: string): boolean {
    const user = this.getCurrentUser();
    return user?.roles.includes(role) || false;
  }

  /**
   * Verifica si el usuario tiene un permiso específico
   */
  hasPermission(permission: string): boolean {
    const user = this.getCurrentUser();
    return user?.permissions.includes(permission) || false;
  }

  /**
   * Verifica si inició sesión como ADMIN (no basta con tener el rol)
   */
  isAdmin(): boolean {
    return this.tokenService.getUserFromToken()?.activeRole === 'ADMIN';
  }

  /**
   * Verifica si el usuario es AUTORIDAD
   */
  isAutoridad(): boolean {
    return this.hasRole('AUTORIDAD');
  }

  /**
   * Verifica si el usuario es OPERADOR
   */
  isOperador(): boolean {
    return this.hasRole('OPERADOR');
  }

  // ========== INFORMACIÓN DEL USUARIO ==========

  /**
   * Obtiene el nombre completo del usuario
   */
  getFullName(): string {
    const user = this.getCurrentUser();
    if (!user) return '';
    return `${user.firstName} ${user.lastName}`.trim();
  }

  /**
   * Obtiene las iniciales del usuario
   */
  getInitials(): string {
    const user = this.getCurrentUser();
    if (!user) return '';
    
    const firstInitial = user.firstName?.charAt(0) || '';
    const lastInitial = user.lastName?.charAt(0) || '';
    return `${firstInitial}${lastInitial}`.toUpperCase();
  }

  /**
   * Obtiene el email del usuario
   */
  getEmail(): string {
    return this.getCurrentUser()?.email || '';
  }

  /**
   * Obtiene la foto de perfil del usuario
   */
  getProfilePicture(): string | undefined {
    return this.getCurrentUser()?.profilePicture;
  }
}