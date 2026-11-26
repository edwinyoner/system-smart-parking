import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Utilidades de horario de turnos. Mismas reglas que el dominio Shift del backend:
 * inicio y fin incluidos, al minuto, y un turno puede cruzar la medianoche (20:00 - 05:59).
 */

/** "06:00:00" o "06:00" -> minutos desde las 00:00 */
function toMinutes(time: string): number {
  const [h, m] = time.split(':').map(Number);
  return h * 60 + m;
}

/** El fin es anterior al inicio: el turno termina al día siguiente */
export function crossesMidnight(start?: string | null, end?: string | null): boolean {
  return !!start && !!end && toMinutes(end) < toMinutes(start);
}

/** Duración contando el minuto final: 06:00 - 13:59 son 8 h */
export function shiftDurationHours(start?: string | null, end?: string | null): number | null {
  if (!start || !end) return null;
  const diff = toMinutes(end) - toMinutes(start);
  return (((diff % 1440) + 1440) % 1440 + 1) / 60;
}

/** 8 -> "8 h"; 7.5 -> "7 h 30 min" */
export function formatShiftDuration(hours?: number | null): string {
  if (hours == null) return 'N/A';
  const total = Math.round(hours * 60);
  const h = Math.floor(total / 60);
  const m = total % 60;
  return m > 0 ? `${h} h ${m} min` : `${h} h`;
}

/**
 * Validador de grupo: inicio y fin no pueden ser iguales (no se sabría si dura un minuto
 * o el día entero). Que el fin sea menor que el inicio es válido: cruza la medianoche.
 */
export const shiftTimeValidator: ValidatorFn = (form: AbstractControl): ValidationErrors | null => {
  const start: string | null = form.get('startTime')?.value;
  const end: string | null = form.get('endTime')?.value;
  if (start && end && toMinutes(start) === toMinutes(end)) {
    return { sameTime: true };
  }
  return null;
};

/** Tramos [inicio, fin] en minutos del día; un turno que cruza medianoche se parte en dos */
function minuteRanges(start: string, end: string): [number, number][] {
  const s = toMinutes(start);
  const e = toMinutes(end);
  return e >= s ? [[s, e]] : [[s, 1439], [0, e]];
}

/**
 * Dos turnos comparten al menos un minuto. Misma regla que Shift.overlapsWith del backend:
 * 06:00-13:59 y 14:00-21:59 no se solapan; 06:00-14:00 y 14:00-21:59 sí.
 */
export function shiftsOverlap(aStart?: string | null, aEnd?: string | null,
                              bStart?: string | null, bEnd?: string | null): boolean {
  if (!aStart || !aEnd || !bStart || !bEnd) return false;
  return minuteRanges(aStart, aEnd).some(([a0, a1]) =>
    minuteRanges(bStart, bEnd).some(([b0, b1]) => a0 <= b1 && b0 <= a1));
}

/** "06:00:00" -> "06:00" */
export function hhmm(time?: string | null): string {
  return time ? time.substring(0, 5) : '';
}
