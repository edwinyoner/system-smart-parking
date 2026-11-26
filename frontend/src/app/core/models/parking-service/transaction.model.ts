// src/app/core/models/parking-service/transaction.model.ts
// Estadías de parking-service (/parking-service/v1/transactions).

export type TransactionStatus = 'ACTIVE' | 'COMPLETED' | 'CANCELLED';
export type TransactionPaymentStatus = 'PENDING' | 'PAID' | 'NOT_REQUIRED' | 'REFUNDED' | 'OVERDUE';
/** MANUAL: operador desde el panel. CAMERA_AI: cámara (sola o confirmada por un operador) */
export type TransactionMethod = 'MANUAL' | 'CAMERA_AI';

// ========================= PETICIONES =========================

/**
 * Entrada manual. El operador no se envía: el backend lo toma del token.
 * Espacio y documento son opcionales (sin espacio, se descuenta uno libre al azar de la zona;
 * el documento solo es obligatorio si el parqueo lo exige).
 */
export interface TransactionEntryRequest {
  plateNumber: string;
  parkingId: number;
  zoneId: number;
  spaceId?: number | null;
  documentTypeId?: number | null;
  documentNumber?: string | null;
  customerFirstName?: string | null;
  customerLastName?: string | null;
  customerPhone?: string | null;
  customerEmail?: string | null;
  notes?: string | null;
}

/** Salida manual, por estadía o por placa. Documento solo si el parqueo lo exige */
export interface TransactionExitRequest {
  transactionId?: number | null;
  plateNumber?: string | null;
  exitDocumentTypeId?: number | null;
  exitDocumentNumber?: string | null;
  notes?: string | null;
}

// ========================= RESPUESTAS =========================

export interface TransactionDto {
  id: number;
  plateNumber: string;
  customerName?: string | null;     // null: entró solo con la placa
  parkingName: string;
  zoneName: string;
  spaceCode: string;
  entryTime: string;
  exitTime?: string;
  duration: string;
  totalAmount?: number;
  status: TransactionStatus;
  paymentStatus: TransactionPaymentStatus;
  createdAt: string;
}

export interface TransactionDocument {
  type?: string | null;             // DNI, CE, PASSPORT
  number: string;
}

export interface TransactionDetailResponse {
  id: number;
  status: TransactionStatus;
  paymentStatus: TransactionPaymentStatus;

  vehicle: { id: number; plateNumber: string };
  /** null si entró solo con la placa */
  customer?: {
    id: number;
    documentType?: string | null;
    documentNumber: string;
    name: string;
    phone?: string;
    email?: string;
  } | null;
  /** requireDriverDocument: la salida exige el mismo documento de entrada (si se entró con uno) */
  parking: { id: number; name: string; code: string; requireDriverDocument?: boolean };
  zone: { id: number; name: string; code: string };
  space: { id: number; code: string };

  entryTime: string;
  exitTime?: string;
  durationMinutes?: number;
  durationFormatted: string;

  entryDocument?: TransactionDocument | null;
  exitDocument?: TransactionDocument | null;

  rate: { id: number; name: string; hourlyRate: number };

  calculatedAmount?: number;
  discountAmount: number;
  totalAmount?: number;
  currency: string;

  // Se paga antes de salir; cada pago está en payment-service (/payments/by-source/PARKING_STAY/{id})
  payment?: {
    paidAmount: number;       // pagado, neto de devoluciones
    paidAt?: string;          // último pago
    amountDue: number;        // falta pagar para salir ahora (0 si ya salió)
    exitDeadline?: string;    // hasta cuándo puede salir sin pagar más
  };

  // Cómo y quién (operador null: la cámara registró sola)
  entryMethod: TransactionMethod;
  exitMethod?: TransactionMethod | null;
  entryOperatorId?: number | null;
  exitOperatorId?: number | null;

  entryPhotoUrl?: string;
  exitPhotoUrl?: string;
  entryPlateConfidence?: number;
  exitPlateConfidence?: number;

  receiptSent: boolean;
  receiptSentAt?: string;
  receiptWhatsAppStatus?: string;
  receiptEmailStatus?: string;

  notes?: string;
  cancellationReason?: string;

  createdAt: string;
  updatedAt: string;
}

export interface ActiveTransactionDto {
  id: number;
  vehicleId: number;
  plateNumber: string;

  // Conductor (todo null si entró solo con la placa)
  customerId?: number | null;
  customerName?: string | null;
  customerPhone?: string | null;
  customerEmail?: string | null;
  documentType?: string | null;
  documentNumber?: string | null;

  parkingId: number;
  parkingName: string;
  zoneId: number;
  zoneName: string;
  spaceId: number;
  spaceCode: string;

  entryTime: string;
  elapsedMinutes: number;
  elapsedFormatted: string;

  hourlyRate: number;
  currentAmount: number;          // lo que falta pagar si saliera ahora
  currency: string;

  maxRecommendedMinutes: number;
  isOverdue: boolean;
  requiresAttention: boolean;

  entryOperatorId?: number | null;
  entryMethod: TransactionMethod;
  entryPhotoUrl?: string;
  plateConfidence?: number;

  notes?: string;
  createdAt: string;
}
