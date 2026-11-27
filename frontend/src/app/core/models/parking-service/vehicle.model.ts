// src/app/core/models/parking-service/vehicle.model.ts
import { HistoryTotals, StayHistory } from './customer.model';

/** Vehículo: se crea solo por placa (entrada o infracción) */
export interface Vehicle {
  id: number;
  plateNumber: string;
  totalVisits: number;
  /** Conductores distintos que entraron con documento */
  driverCount: number;
  /** Estadía en curso (null si no está dentro) */
  activeStayId: number | null;
  firstSeenDate: string | null;
  lastSeenDate: string | null;
  createdAt: string;
}

export interface VehicleDetail {
  vehicle: Vehicle;
  drivers: {
    customerId: number;
    fullName: string | null;
    documentTypeCode: string | null;
    documentNumber: string;
    usageCount: number;
  }[];
  activeStay: StayHistory | null;
  totals: HistoryTotals;
}
