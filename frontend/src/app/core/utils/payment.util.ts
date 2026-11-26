import { PaymentConcept, PaymentStatus } from '../models/payment-service/payment.model';

/**
 * Textos y colores de los pagos. Paleta del proyecto: verde (bien), amarillo (en espera),
 * rojo (falló) y gris (cerrado sin efecto). Sin azules ni morados.
 */

export const PAYMENT_STATUSES: { value: PaymentStatus; label: string; badge: string; icon: string }[] = [
  { value: 'PENDING', label: 'Esperando pago', badge: 'bg-warning text-dark', icon: 'fa-hourglass-half' },
  { value: 'COMPLETED', label: 'Pagado', badge: 'bg-success', icon: 'fa-check-circle' },
  { value: 'PARTIALLY_REFUNDED', label: 'Devuelto en parte', badge: 'bg-warning text-dark', icon: 'fa-rotate-left' },
  { value: 'REFUNDED', label: 'Devuelto', badge: 'bg-secondary', icon: 'fa-rotate-left' },
  { value: 'FAILED', label: 'Rechazado', badge: 'bg-danger', icon: 'fa-times-circle' },
  { value: 'EXPIRED', label: 'Vencido', badge: 'bg-secondary', icon: 'fa-clock' },
  { value: 'CANCELLED', label: 'Anulado', badge: 'bg-secondary', icon: 'fa-ban' },
];

export const PAYMENT_CONCEPTS: { value: PaymentConcept; label: string; icon: string }[] = [
  { value: 'PARKING_STAY', label: 'Estadía', icon: 'fa-car' },
  { value: 'FINE', label: 'Multa', icon: 'fa-triangle-exclamation' },
];

export function paymentStatusLabel(status: PaymentStatus): string {
  return PAYMENT_STATUSES.find(s => s.value === status)?.label ?? status;
}

export function paymentStatusBadge(status: PaymentStatus): string {
  return PAYMENT_STATUSES.find(s => s.value === status)?.badge ?? 'bg-secondary';
}

export function paymentStatusIcon(status: PaymentStatus): string {
  return PAYMENT_STATUSES.find(s => s.value === status)?.icon ?? 'fa-circle';
}

export function paymentConceptLabel(concept: PaymentConcept): string {
  return PAYMENT_CONCEPTS.find(c => c.value === concept)?.label ?? concept;
}

/** Ruta del panel donde se ve lo que se pagó (la estadía o la multa) */
export function paymentSourceLink(concept: PaymentConcept, sourceId: number): string[] {
  return concept === 'FINE' ? ['/infractions', String(sourceId)] : ['/transactions', String(sourceId)];
}

/**
 * Estado de pago de una estadía en parking-service (no es el de un pago):
 * PENDING (debe), PAID (pagada), NOT_REQUIRED (salió en el tiempo de gracia),
 * REFUNDED (se devolvió tras salir), OVERDUE (vencida).
 */
export function stayPaymentStatusLabel(status: string): string {
  switch (status) {
    case 'PAID': return 'Pagado';
    case 'PENDING': return 'Pendiente';
    case 'NOT_REQUIRED': return 'Sin costo';
    case 'REFUNDED': return 'Devuelto';
    case 'OVERDUE': return 'Vencido';
    default: return status;
  }
}

/** Color de CoreUI (c-badge color) para el estado de pago de una estadía */
export function stayPaymentStatusColor(status: string): string {
  switch (status) {
    case 'PAID': return 'success';
    case 'PENDING': return 'warning';
    case 'OVERDUE': return 'danger';
    default: return 'secondary';
  }
}

/** Segundos que faltan hasta una fecha ISO (0 si ya pasó) */
export function secondsUntil(iso?: string, now = Date.now()): number {
  if (!iso) return 0;
  return Math.max(0, Math.floor((new Date(iso).getTime() - now) / 1000));
}

/** 125 → "2:05" */
export function formatCountdown(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}:${s.toString().padStart(2, '0')}`;
}
