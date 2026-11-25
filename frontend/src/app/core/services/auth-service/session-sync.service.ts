import { Injectable } from '@angular/core';
import { EMPTY, Observable, catchError, concatMap, exhaustMap, filter, map, of } from 'rxjs';
import { liveRefresh } from '../../utils/live-refresh.util';
import { AuthService } from './auth.service';
import { CurrentUserService } from './current-user.service';
import { UserService } from './user.service';

/** Cada cuánto se consulta si la sesión sigue activa */
const SYNC_INTERVAL_MS = 10_000;

/**
 * Mantiene la sesión al día con el servidor.
 *
 * - Si otro usuario cambió los datos, actualiza cabecera, menú y perfil.
 * - Si la cuenta se desactivó o eliminó, o cambió la contraseña, el backend
 *   responde 401 y errorInterceptor cierra la sesión.
 * - Si cambiaron los permisos del rol, renueva el token: los demás microservicios
 *   (parking-service...) leen los permisos del token y así los ven de inmediato.
 */
@Injectable({
  providedIn: 'root'
})
export class SessionSyncService {

  constructor(
    private userService: UserService,
    private currentUserService: CurrentUserService,
    private authService: AuthService
  ) {}

  /**
   * Sincroniza al suscribirse, periódicamente y al volver a la ventana.
   */
  sync(): Observable<void> {
    return liveRefresh(SYNC_INTERVAL_MS, 0).pipe(
      // exhaustMap: no lanza otra consulta mientras haya una pendiente
      exhaustMap(() => this.userService.getMe().pipe(
        // Un fallo de red no debe detener la sincronización
        catchError(() => EMPTY)
      )),
      filter(user => user.id === this.currentUserService.getCurrentUser()?.id),
      concatMap(user => {
        const before = this.currentUserService.getCurrentUser()?.permissions ?? [];
        this.currentUserService.applyProfile(user);
        // Un fallo al renovar no debe detener la sincronización
        return this.samePermissions(before, user.activePermissions ?? before)
          ? of(undefined)
          : this.authService.refreshToken().pipe(map(() => undefined), catchError(() => EMPTY));
      }),
      map(() => undefined)
    );
  }

  /** Mismos permisos sin importar el orden */
  private samePermissions(a: string[], b: string[]): boolean {
    const set = new Set(a);
    return a.length === b.length && b.every(permission => set.has(permission));
  }
}
