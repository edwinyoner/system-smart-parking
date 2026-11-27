// src/app/core/utils/infraction.util.ts
import {
  InfractionDisplayStatus,
  InfractionSeverity,
  InfractionStatusFilter,
  InfractionType
} from '../models/parking-service/infraction.model';
import { StatusInfo } from './parking.util';

export const INFRACTION_TYPES: { value: InfractionType; label: string; icon: string }[] = [
  { value: 'WRONG_SPACE',     label: 'Espacio indebido',        icon: 'fa-square-parking' },
  { value: 'DOUBLE_PARKING',  label: 'Doble fila u obstrucción', icon: 'fa-car-burst' },
  { value: 'OVERSTAY',        label: 'Exceso de tiempo',        icon: 'fa-hourglass-end' },
  { value: 'UNPAID_EXIT',     label: 'Salida sin pagar',        icon: 'fa-money-bill-transfer' },
  { value: 'NO_REGISTRATION', label: 'Sin registro de entrada', icon: 'fa-clipboard-question' },
  { value: 'ABANDONED',       label: 'Vehículo abandonado',     icon: 'fa-car-side' },
  { value: 'DAMAGE',          label: 'Daños a la propiedad',    icon: 'fa-hammer' },
  { value: 'OTHER',           label: 'Otra',                    icon: 'fa-circle-exclamation' }
];

export const INFRACTION_SEVERITIES: { value: InfractionSeverity; label: string; badge: string }[] = [
  { value: 'MINOR',    label: 'Leve',      badge: 'bg-secondary' },
  { value: 'MODERATE', label: 'Moderada',  badge: 'bg-info' },
  { value: 'SEVERE',   label: 'Grave',     badge: 'bg-warning text-dark' },
  { value: 'CRITICAL', label: 'Muy grave', badge: 'bg-danger' }
];

const STATUS: Record<InfractionDisplayStatus, StatusInfo> = {
  PENDING:   { label: 'Pendiente',    badge: 'bg-warning text-dark', icon: 'fa-hourglass-half' },
  OVERDUE:   { label: 'Vencida',      badge: 'bg-danger',            icon: 'fa-calendar-xmark' },
  PAID:      { label: 'Pagada',       badge: 'bg-success',           icon: 'fa-circle-check' },
  DISMISSED: { label: 'Anulada',      badge: 'bg-secondary',         icon: 'fa-ban' },
  WARNING:   { label: 'Amonestación', badge: 'bg-info',              icon: 'fa-comment-dots' }
};

export const INFRACTION_STATUS_FILTERS: { value: InfractionStatusFilter; label: string }[] = [
  { value: 'ALL',       label: 'Todas' },
  { value: 'OPEN',      label: 'Pendientes' },
  { value: 'OVERDUE',   label: 'Vencidas' },
  { value: 'PAID',      label: 'Pagadas' },
  { value: 'DISMISSED', label: 'Anuladas' },
  { value: 'WARNING',   label: 'Amonestaciones' }
];

/** Motivos frecuentes de anulación (se pueden editar antes de enviar) */
export const DISMISS_REASONS = [
  'Registro duplicado',
  'Placa mal registrada',
  'El conductor justificó la situación',
  'Error del operador'
];

export const INFRACTION_EVIDENCE_MAX = 5;
export const INFRACTION_FINE_MAX = 10000;

export function infractionTypeLabel(type: string | null | undefined): string {
  return INFRACTION_TYPES.find(t => t.value === type)?.label ?? type ?? '';
}

export function infractionTypeIcon(type: string | null | undefined): string {
  return INFRACTION_TYPES.find(t => t.value === type)?.icon ?? 'fa-circle-exclamation';
}

export function severityInfo(severity: string | null | undefined): { label: string; badge: string } {
  return INFRACTION_SEVERITIES.find(s => s.value === severity) ?? { label: severity ?? '', badge: 'bg-secondary' };
}

export function infractionStatusInfo(status: string | null | undefined): StatusInfo {
  return STATUS[status as InfractionDisplayStatus] ?? { label: status ?? '', badge: 'bg-secondary', icon: 'fa-circle-question' };
}

/** Una URL http(s) válida (la misma regla del backend) */
export function isEvidenceUrl(value: string): boolean {
  try {
    const url = new URL(value.trim());
    return (url.protocol === 'http:' || url.protocol === 'https:') && !!url.hostname && value.trim().length <= 500;
  } catch {
    return false;
  }
}

/** Fecha y hora local para un input datetime-local (yyyy-MM-ddTHH:mm) */
export function localDateTimeInput(date: Date = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
