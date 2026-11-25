import { Observable, fromEvent, map, merge, timer } from 'rxjs';

/**
 * Emite cada cierto tiempo y al volver a la ventana.
 * Quien se suscribe decide qué consultar y cuándo dejar de hacerlo.
 */
export function liveRefresh(intervalMs: number, initialDelayMs = intervalMs): Observable<void> {
  return merge(
    timer(initialDelayMs, intervalMs),
    fromEvent(window, 'focus')
  ).pipe(map(() => undefined));
}
