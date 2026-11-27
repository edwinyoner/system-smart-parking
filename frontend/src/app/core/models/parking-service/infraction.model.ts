// src/app/core/models/parking-service/infraction.model.ts

/** Tipos de infracción (los mismos del backend) */
export type InfractionType =
  | 'OVERSTAY' | 'UNPAID_EXIT' | 'WRONG_SPACE' | 'DOUBLE_PARKING'
  | 'NO_REGISTRATION' | 'ABANDONED' | 'DAMAGE' | 'OTHER';

export type InfractionSeverity = 'MINOR' | 'MODERATE' | 'SEVERE' | 'CRITICAL';

/** Lo que ve el usuario: pendiente, vencida, pagada, anulada o amonestación */
export type InfractionDisplayStatus = 'PENDING' | 'OVERDUE' | 'PAID' | 'DISMISSED' | 'WARNING';

/** Filtro de estado de la lista: OPEN incluye las vencidas */
export type InfractionStatusFilter = 'ALL' | 'OPEN' | 'OVERDUE' | 'RESOLVED' | 'PAID' | 'DISMISSED' | 'WARNING';

export interface Infraction {
  id: number;
  infractionCode: string;
  parkingId: number;
  parkingName: string | null;
  parkingCode: string | null;
  zoneId: number | null;
  zoneName: string | null;
  zoneCode: string | null;
  spaceId: number | null;
  spaceCode: string | null;
  transactionId: number | null;
  vehicleId: number;
  plateNumber: string;
  customerId: number | null;
  customerName: string | null;
  infractionType: InfractionType;
  severity: InfractionSeverity;
  description: string;
  evidence: string[];
  detectedAt: string;
  detectedBy: number | null;
  detectedByName: string | null;
  detectionMethod: string;
  fineAmount: number | null;
  currency: string;
  fineDueDate: string | null;
  paidAmount: number;
  amountDue: number;
  finePaid: boolean;
  finePaidAt: string | null;
  paymentReference: string | null;
  status: 'PENDING' | 'RESOLVED';
  displayStatus: InfractionDisplayStatus;
  overdue: boolean;
  /** Se puede cobrar ahora */
  payable: boolean;
  /** Pendiente y sin pagos: se pueden cambiar sus datos o su multa */
  editable: boolean;
  resolutionType: 'PAID' | 'DISMISSED' | 'WARNING' | null;
  resolution: string | null;
  resolvedAt: string | null;
  resolvedBy: number | null;
  notes: string | null;
  createdAt: string;
  createdBy: number | null;
  updatedAt: string;
  updatedBy: number | null;
}

export interface InfractionSummary {
  open: number;
  overdue: number;
  paid: number;
  dismissed: number;
  warning: number;
  amountPending: number;
}

/** Quien registra sale del token; zona y espacio se toman de la estadía si se indica */
export interface RegisterInfractionRequest {
  parkingId: number;
  zoneId?: number | null;
  spaceId?: number | null;
  transactionId?: number | null;
  plateNumber: string;
  infractionType: InfractionType;
  severity: InfractionSeverity;
  description: string;
  evidence?: string[];
  fineAmount?: number | null;
  fineDueDate?: string | null;   // yyyy-MM-dd
  detectedAt?: string | null;    // yyyy-MM-ddTHH:mm:ss (hora local)
  notes?: string | null;
}

export interface EditInfractionRequest {
  infractionType: InfractionType;
  severity: InfractionSeverity;
  description: string;
  evidence?: string[];
  notes?: string | null;
}

export interface InfractionFilters {
  status?: InfractionStatusFilter | null;
  parkingId?: number | null;
  type?: InfractionType | null;
  search?: string | null;
  from?: string | null;   // yyyy-MM-dd
  to?: string | null;
}
