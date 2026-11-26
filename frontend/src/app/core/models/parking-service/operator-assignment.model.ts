// src/app/core/models/parking-service/operator-assignment.model.ts

export type AssignmentScope = 'PARKING' | 'ZONE';
export type AssignmentStatus = 'PENDING' | 'ACTIVE' | 'ENDED';

/**
 * Asignación de un operador a un parqueo (o a una zona) en un turno.
 * Fechas inclusivas "yyyy-MM-dd"; sin fin = indefinida.
 */
export interface OperatorAssignment {
  id: number;
  operatorId: number;
  operatorName?: string;
  parkingId: number;
  parkingName?: string;
  parkingCode?: string;
  scope: AssignmentScope;
  zoneId?: number;
  zoneName?: string;
  zoneCode?: string;
  shiftId: number;
  shiftName?: string;
  shiftStartTime?: string;
  shiftEndTime?: string;
  shiftCrossesMidnight?: boolean;
  startDate: string;
  endDate?: string;
  status: AssignmentStatus;
  onDutyNow: boolean;            // en este momento le toca atender
  createdAt?: string;
  createdBy?: number;
  updatedAt?: string;
  updatedBy?: number;
}

/** zoneId vacío = todo el parqueo; startDate vacío = hoy; endDate vacío = indefinida */
export interface AssignOperatorRequest {
  operatorId: number;
  parkingId: number;
  zoneId?: number | null;
  shiftId: number;
  startDate?: string;
  endDate?: string | null;
}

/** Cambiar fechas: el inicio solo cambia si aún no empezó; endDate null = indefinida */
export interface UpdateOperatorAssignmentRequest {
  startDate?: string;
  endDate?: string | null;
}
