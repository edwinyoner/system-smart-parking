import { ConfirmationMode, PaymentChannel } from './payment-type.model';

/** Qué se paga: una estadía (transacción de parking-service) o una multa (infracción) */
export type PaymentConcept = 'PARKING_STAY' | 'FINE';

/**
 * Estados de un pago en payment-service:
 * PENDING (QR esperando) → COMPLETED / FAILED / EXPIRED / CANCELLED;
 * COMPLETED → PARTIALLY_REFUNDED → REFUNDED.
 */
export type PaymentStatus =
  | 'PENDING'
  | 'COMPLETED'
  | 'FAILED'
  | 'EXPIRED'
  | 'CANCELLED'
  | 'PARTIALLY_REFUNDED'
  | 'REFUNDED';

/** Pago tal como lo devuelve payment-service (PaymentResponse) */
export interface Payment {
  id: number;
  concept: PaymentConcept;
  sourceId: number;
  description?: string;
  plateNumber?: string;

  // Tipo de pago copiado al cobrar (no cambia si luego se edita el tipo)
  paymentTypeId: number;
  paymentTypeCode: string;
  paymentTypeName: string;
  confirmationMode: ConfirmationMode;
  channel: PaymentChannel;

  amount: number;
  currency: string;
  status: PaymentStatus;

  // QR (solo pagos automáticos)
  gatewayReference?: string;
  qrPayload?: string;
  expiresAt?: string;

  operationNumber?: string;
  operatorId?: number;
  paidAt?: string;
  failureReason?: string;
  cancelReason?: string;

  // Devoluciones
  refundedAmount: number;
  refundableAmount: number;
  refundReason?: string;
  refundedAt?: string;
  refundedBy?: number;

  /** false: parking-service aún no recibió el aviso (se reintenta cada minuto) */
  settlementNotified: boolean;

  createdAt: string;
  createdBy?: number;
  updatedAt?: string;
  updatedBy?: number;
}

/** El monto no se envía: payment-service lo pregunta a parking-service */
export interface CreatePaymentRequest {
  concept: PaymentConcept;
  sourceId: number;
  paymentTypeId: number;
  channel: PaymentChannel;
  operationNumber?: string;
}

export interface RefundPaymentRequest {
  amount: number;
  reason: string;
}

export interface PaymentFilters {
  status?: PaymentStatus | '';
  concept?: PaymentConcept | '';
  paymentTypeId?: number | null;
  from?: string;   // yyyy-MM-dd
  to?: string;     // yyyy-MM-dd (incluido)
  search?: string;
}
