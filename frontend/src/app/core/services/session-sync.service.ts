import { Injectable } from '@angular/core';
import { EMPTY, Observable, catchError, exhaustMap, filter, map, tap } from 'rxjs';
import { liveRefresh } from '../utils/live-refresh.util';
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
 */
@Injectable({
  providedIn: 'root'
})
export class SessionSyncService {

  constructor(
    private userService: UserService,
    private currentUserService: CurrentUserService
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
      tap(user => this.currentUserService.applyProfile(user)),
      map(() => undefined)
    );
  }
}
