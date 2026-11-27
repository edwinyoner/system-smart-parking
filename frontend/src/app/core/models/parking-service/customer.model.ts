// src/app/core/models/parking-service/customer.model.ts

/** Cliente: conductor identificado con documento al entrar (se crea solo) */
export interface Customer {
  id: number;
  documentTypeId: number | null;
  documentTypeCode: string | null;
  documentNumber: string;
  firstName: string | null;
  lastName: string | null;
  fullName: string | null;
  phone: string | null;
  email: string | null;
  address: string | null;
  totalVisits: number;
  vehicleCount: number;
  firstSeenDate: string | null;
  lastSeenDate: string | null;
  createdAt: string;
  updatedAt: string | null;
}

export interface HistoryTotals {
  stays: number;
  /** Pagado por sus estadías, neto de devoluciones */
  totalPaid: number;
  infractions: number;
  /** Falta cobrar de sus multas pendientes */
  pendingFines: number;
}

export interface CustomerDetail {
  customer: Customer;
  vehicles: { vehicleId: number; plateNumber: string | null; usageCount: number; activeStayId: number | null }[];
  totals: HistoryTotals;
}

/** Una estadía del historial de un cliente o de un vehículo */
export interface StayHistory {
  id: number;
  plateNumber: string | null;
  parkingId: number;
  parkingName: string | null;
  zoneName: string | null;
  spaceCode: string | null;
  entryTime: string;
  exitTime: string | null;
  durationMinutes: number | null;
  status: string;
  paymentStatus: string;
  entryMethod: string | null;
  totalAmount: number | null;
  paidAmount: number;
  currency: string;
}

export interface UpdateCustomerContactRequest {
  phone: string | null;
  email: string | null;
  address: string | null;
}
