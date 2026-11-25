import { Injectable } from '@angular/core';
import { AuthContextService } from './auth-context.service';
import { CurrentUserService } from './current-user.service';
import { User } from '../../models/auth-service/user.model';

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
      return 'Es tu propia cuenta: no puedes desactivarla, eliminarla ni cambiar sus roles o su correo.';
    }

    const isAdmin = user.roles?.some(role => role.name === 'ADMIN');
    if (isAdmin && !this.canManageAdmins()) {
      return 'Este usuario tiene el rol ADMIN: solo otro usuario con el rol ADMIN activo puede administrarlo.';
    }

    return null;
  }

  /**
   * Motivo por el que no se pueden editar los datos (nombre, teléfono...).
   * La cuenta propia sí se edita: lo bloqueado ahí son el estado, los roles y el correo.
   * @returns null si la edición está permitida
   */
  getEditBlockedReason(user: User): string | null {
    const isAdmin = user.roles?.some(role => role.name === 'ADMIN');
    return isAdmin && !this.canManageAdmins()
      ? 'Este usuario tiene el rol ADMIN: solo otro usuario con el rol ADMIN activo puede editarlo.'
      : null;
  }

  /** Misma regla que el backend: a un ADMIN solo lo edita quien entró como ADMIN */
  canEdit(user: User): boolean {
    return this.getEditBlockedReason(user) === null;
  }

  /** Solo quien inició sesión como ADMIN puede administrar o conceder ADMIN */
  canManageAdmins(): boolean {
    return this.authContext.isActiveRole('ADMIN');
  }
}
