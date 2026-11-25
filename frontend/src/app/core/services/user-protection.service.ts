import { Injectable } from '@angular/core';
import { AuthContextService } from './auth-context.service';
import { CurrentUserService } from './current-user.service';
import { User } from '../models/user.model';

/**
 * Reglas de protección de usuarios para la interfaz.
 * Solo orientan al usuario: quien las hace cumplir es auth-service.
 */
@Injectable({
  providedIn: 'root'
})
export class UserProtectionService {

  constructor(
    private currentUserService: CurrentUserService,
    private authContext: AuthContextService
  ) {}

  /**
   * Motivo por el que no se puede cambiar el estado, los roles o eliminar.
   * @returns null si la operación está permitida
   */
  getBlockedReason(user: User): string | null {
    if (this.currentUserService.getCurrentUser()?.id === user.id) {
      return 'Es tu propia cuenta: no puedes desactivarla, eliminarla ni cambiar sus roles.';
    }

    const isAdmin = user.roles?.some(role => role.name === 'ADMIN');
    if (isAdmin && !this.canManageAdmins()) {
      return 'Este usuario tiene el rol ADMIN: solo otro usuario con el rol ADMIN activo puede administrarlo.';
    }

    return null;
  }

  /** Solo quien inició sesión como ADMIN puede administrar o conceder ADMIN */
  canManageAdmins(): boolean {
    return this.authContext.isActiveRole('ADMIN');
  }
}
