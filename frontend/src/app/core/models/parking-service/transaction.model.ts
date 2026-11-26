// src/app/core/models/parking-service/transaction.model.ts
/**
 * Estados posibles de una Transacción
 */
export type TransactionStatus = 'ACTIVE' | 'COMPLETED' | 'CANCELLED';

/**
 * Estado de pago de una estadía (lo calcula parking-service con los avisos de payment-service):
 * PENDING (debe), PAID (pagada), NOT_REQUIRED (salió en el tiempo de gracia),
 * REFUNDED (se devolvió el pago tras salir), OVERDUE (vencida).
 */
export type TransactionPaymentStatus = 'PENDING' | 'PAID' | 'NOT_REQUIRED' | 'REFUNDED' | 'OVERDUE';

/**
 * Métodos de registro (entrada/salida)
 */
export type TransactionMethod = 'MANUAL' | 'CAMERA_AI' | 'SENSOR';

// ========================= REQUEST DTOs =========================

/**
 * Request para registrar ENTRADA de vehículo.
 * 
 * IMPORTANTE: Solo se registra la PLACA. 
 * Información adicional del vehículo vendrá de SUNARP en futuras versiones.
 */
export interface TransactionEntryRequest {
  // Ubicación
  parkingId: number;              // Backend lo requiere
  zoneId: number;
  spaceId: number;                // CORREGIDO (era parkingSpaceId)

  // Vehículo - SOLO PLACA
  plateNumber: string;

  // Documento
  documentTypeId: number;
  documentNumber: string;

  // Cliente - NOMBRES SEPARADOS
  customerFirstName: string;      // CORREGIDO (antes era customerName)
  customerLastName: string;
  customerEmail?: string;
  customerPhone?: string;

  // Operador
  operatorId: number;

  // Evidencia (opcional)
  entryMethod?: TransactionMethod;
  photoUrl?: string;
  plateConfidence?: number;

  // Observaciones
  notes?: string;
}

/**
 * Request para registrar SALIDA de vehículo.
 */
export interface TransactionExitRequest {
  // Identificar transacción (uno u otro)
  transactionId?: number;
  plateNumber?: string;

  // Documento de salida (SEGURIDAD - debe coincidir con entrada)
  exitDocumentTypeId: number;
  exitDocumentNumber: string;

  // Operador
  operatorId: number;

  // Evidencia (opcional)
  exitMethod?: TransactionMethod;
  photoUrl?: string;
  plateConfidence?: number;

  // Observaciones
  notes?: string;
}

// ========================= RESPONSE DTOs =========================

/**
 * DTO simplificado para LISTADOS de transacciones.
 */
export interface TransactionDto {
  id: number;
  plateNumber: string;
  customerName: string;
  parkingName: string;
  zoneName: string;
  spaceCode: string;
  entryTime: string;
  exitTime?: string;
  duration: string;               // Formato: "3h 30min"
  totalAmount?: number;
  status: TransactionStatus;
  paymentStatus: TransactionPaymentStatus;
  createdAt: string;
}

/**
 * DTO completo con TODOS los detalles de una transacción.
 */
export interface TransactionDetailResponse {
  id: number;
  status: TransactionStatus;
  paymentStatus: TransactionPaymentStatus;

  // ========================= ENTIDADES ANIDADAS =========================

  vehicle: {
    id: number;
    plateNumber: string;
    // brand, color no existen todavía (futuro SUNARP)
  };

  customer: {
    id: number;
    documentNumber: string;
    name: string;                 // fullName del backend
    phone?: string;
    email?: string;
  };

  parking: {                      // Backend sí lo retorna
    id: number;
    name: string;
    code: string;
  };

  zone: {
    id: number;
    name: string;
    code: string;
  };

  space: {
    id: number;
    code: string;
  };

  // ========================= TIEMPOS =========================

  entryTime: string;
  exitTime?: string;
  durationMinutes?: number;
  durationFormatted: string;      // "3h 30min"

  // ========================= DOCUMENTOS =========================

  entryDocument: {
    number: string;
  };
  exitDocument?: {
    number: string;
  };

  // ========================= TARIFA =========================

  rate: {
    id: number;
    name: string;
    hourlyRate: number;
  };

  // ========================= MONTOS =========================

  calculatedAmount?: number;
  discountAmount: number;
  totalAmount?: number;
  currency: string;

  // ========================= PAGO =========================

  // Se paga antes de salir. El detalle de cada pago está en payment-service
  // (GET /payments/by-source/PARKING_STAY/{id})
  payment?: {
    paidAmount: number;       // pagado, neto de devoluciones
    paidAt?: string;          // último pago
    amountDue: number;        // falta pagar para salir ahora (0 si ya salió)
    exitDeadline?: string;    // hasta cuándo puede salir sin pagar más
  };

  // ========================= EVIDENCIA =========================

  entryPhotoUrl?: string;
  exitPhotoUrl?: string;
  entryPlateConfidence?: number;
  exitPlateConfidence?: number;

  // ========================= COMPROBANTE =========================

  receiptSent: boolean;
  receiptSentAt?: string;
  receiptWhatsAppStatus?: string;
  receiptEmailStatus?: string;

  // ========================= OBSERVACIONES =========================

  notes?: string;
  cancellationReason?: string;

  // ========================= AUDITORÍA =========================

  createdAt: string;
  updatedAt: string;
}

/**
 * DTO para transacciones ACTIVAS (monitoreo en tiempo real).
 */
export interface ActiveTransactionDto {
  id: number;

  // Vehículo
  vehicleId: number;
  plateNumber: string;

  // Cliente
  customerId: number;
  customerName: string;
  customerPhone?: string;
  customerEmail?: string;
  documentNumber: string;

  // Ubicación
  parkingId: number;           
  parkingName: string;
  zoneId: number;
  zoneName: string;
  spaceId: number;
  spaceCode: string;

  // Tiempos
  entryTime: string;
  elapsedMinutes: number;
  elapsedFormatted: string;       // "2h 15min"

  // Cálculo en tiempo real
  hourlyRate: number;
  currentAmount: number;          // Lo que falta pagar si saliera ahora (descuenta lo ya pagado)
  currency: string;

  // Alertas
  maxRecommendedMinutes: number;
  isOverdue: boolean;
  requiresAttention: boolean;

  // Operador
  entryOperatorId: number;
  entryMethod: TransactionMethod;

  // Evidencia
  entryPhotoUrl?: string;
  plateConfidence?: number;

  // Observaciones
  notes?: string;

  // Auditoría
  createdAt: string;
}