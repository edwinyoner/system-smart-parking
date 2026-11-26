// src/app/core/utils/parking.util.ts
import { AbstractControl, ValidationErrors } from '@angular/forms';

/**
 * Etiquetas, insignias y reglas compartidas de parqueos, zonas, espacios y asignaciones.
 * Las reglas de formato son las mismas que valida el backend (dominio Parking, Zone, Space).
 */

/** Parqueo: 2-20 caracteres, empieza con letra o número; mayúsculas, números, - y _ */
export const PARKING_CODE_PATTERN = /^[A-Z0-9][A-Z0-9_-]{1,19}$/;
/** Zona y espacio: 1-20 caracteres (únicos dentro de su parqueo o zona) */
export const ZONE_CODE_PATTERN = /^[A-Z0-9][A-Z0-9_-]{0,19}$/;
export const SPACE_CODE_PATTERN = ZONE_CODE_PATTERN;
/** Cámara: 1-40; sensor: 1-50 (letras, números, punto, dos puntos, guion y guion bajo) */
export const CAMERA_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9_.:-]{0,39}$/;
export const SENSOR_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9_.:-]{0,49}$/;
export const MAX_CAMERAS_PER_ZONE = 10;

export interface StatusInfo {
  label: string;
  badge: string;   // clases de Bootstrap
  icon: string;    // Font Awesome
}

/** Estados de parqueo y de zona (son los mismos) */
const LOCATION_STATUS: Record<string, StatusInfo> = {
  ACTIVE:         { label: 'Activo',            badge: 'bg-success',          icon: 'fa-check-circle' },
  INACTIVE:       { label: 'Inactivo',          badge: 'bg-secondary',        icon: 'fa-pause-circle' },
  MAINTENANCE:    { label: 'En mantenimiento',  badge: 'bg-warning text-dark', icon: 'fa-tools' },
  OUT_OF_SERVICE: { label: 'Fuera de servicio', badge: 'bg-danger',           icon: 'fa-ban' },
};

export const LOCATION_STATUS_OPTIONS = Object.entries(LOCATION_STATUS)
  .map(([value, info]) => ({ value, label: info.label }));

export function locationStatus(status?: string | null): StatusInfo {
  return LOCATION_STATUS[status ?? ''] ?? { label: status ?? 'Desconocido', badge: 'bg-secondary', icon: 'fa-question-circle' };
}

const SPACE_STATUS: Record<string, StatusInfo> = {
  AVAILABLE:      { label: 'Libre',             badge: 'bg-success',          icon: 'fa-check-circle' },
  OCCUPIED:       { label: 'Ocupado',           badge: 'bg-danger',           icon: 'fa-car' },
  MAINTENANCE:    { label: 'En mantenimiento',  badge: 'bg-warning text-dark', icon: 'fa-tools' },
  OUT_OF_SERVICE: { label: 'Fuera de servicio', badge: 'bg-secondary',        icon: 'fa-ban' },
};

/** Estados que se eligen a mano (OCCUPIED solo por entrada de vehículos o detección) */
export const SPACE_MANUAL_STATUS_OPTIONS = ['AVAILABLE', 'MAINTENANCE', 'OUT_OF_SERVICE']
  .map(value => ({ value, label: SPACE_STATUS[value].label }));

export const SPACE_STATUS_OPTIONS = Object.entries(SPACE_STATUS)
  .map(([value, info]) => ({ value, label: info.label }));

export function spaceStatus(status?: string | null): StatusInfo {
  return SPACE_STATUS[status ?? ''] ?? { label: status ?? 'Desconocido', badge: 'bg-secondary', icon: 'fa-question-circle' };
}

export const SPACE_TYPE_OPTIONS: { value: 'PARALLEL' | 'DIAGONAL' | 'PERPENDICULAR'; label: string }[] = [
  { value: 'PERPENDICULAR', label: 'Perpendicular' },
  { value: 'PARALLEL',      label: 'Paralelo' },
  { value: 'DIAGONAL',      label: 'Diagonal' },
];

export function spaceTypeLabel(type?: string | null): string {
  return SPACE_TYPE_OPTIONS.find(t => t.value === type)?.label ?? (type ?? '');
}

const ASSIGNMENT_STATUS: Record<string, StatusInfo> = {
  PENDING: { label: 'Programada', badge: 'bg-warning text-dark', icon: 'fa-hourglass-start' },
  ACTIVE:  { label: 'Vigente',    badge: 'bg-success',          icon: 'fa-user-check' },
  ENDED:   { label: 'Finalizada', badge: 'bg-secondary',        icon: 'fa-user-slash' },
};

export function assignmentStatus(status?: string | null): StatusInfo {
  return ASSIGNMENT_STATUS[status ?? ''] ?? { label: status ?? '', badge: 'bg-secondary', icon: 'fa-question-circle' };
}

/**
 * Fecha local "yyyy-MM-dd". No usar toISOString(): da la fecha en UTC, y en Lima
 * después de las 19:00 ya sería el día siguiente.
 */
export function localDate(date: Date = new Date()): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/** "2026-11-26" -> "26/11/2026" (sin pasar por Date, que lo movería por la zona horaria) */
export function formatLocalDate(value?: string | null, empty = 'Indefinida'): string {
  if (!value) return empty;
  const [y, m, d] = value.substring(0, 10).split('-');
  return `${d}/${m}/${y}`;
}

/** Código sugerido para el espacio n de una zona: NORTE-001 */
export function spaceCode(prefix: string, n: number, digits = 3): string {
  return `${prefix}-${String(n).padStart(digits, '0')}`;
}

/** Mensaje del backend, o el genérico si no lo hay */
export function apiError(err: any, fallback: string): string {
  const e = err?.error;
  if (e?.errors && typeof e.errors === 'object') {
    const first = Object.values(e.errors)[0];
    if (first) return `${e.message ?? fallback}: ${first}`;
  }
  return e?.message || fallback;
}

/** "CAM-01, CAM-02" -> ["CAM-01", "CAM-02"] sin vacíos ni repetidos */
export function parseCameras(value: string | null | undefined): string[] {
  return [...new Set((value ?? '').split(',').map(c => c.trim()).filter(Boolean))];
}

/** Lista de cámaras separada por comas: máximo 10 y cada una con formato válido */
export function cameraListValidator(ctrl: AbstractControl): ValidationErrors | null {
  const cams = parseCameras(ctrl.value);
  if (cams.length > MAX_CAMERAS_PER_ZONE) {
    return { cameras: `Máximo ${MAX_CAMERAS_PER_ZONE} cámaras por zona.` };
  }
  const bad = cams.find(c => !CAMERA_ID_PATTERN.test(c));
  return bad ? { cameras: `Identificador de cámara inválido: ${bad}` } : null;
}

/** "Zona Norte Baja" -> "NORTE-BAJA" (sin tildes; máx. 20 caracteres) */
export function suggestZoneCode(name: string | null | undefined, fallback: string): string {
  const words = (name ?? '')
    .normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .toUpperCase().split(/[^A-Z0-9]+/).filter(w => w && w !== 'ZONA');
  return (words.join('-') || fallback).substring(0, 20).replace(/-+$/, '');
}
