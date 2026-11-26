// src/app/core/models/parking-service/parking.model.ts

export type ParkingStatus = 'ACTIVE' | 'INACTIVE' | 'MAINTENANCE' | 'OUT_OF_SERVICE';

/**
 * Parqueo. La capacidad (zonas, espacios, libres, ocupados) la calcula el backend
 * desde el estado real de cada espacio en cada consulta.
 */
export interface Parking {
  id?: number;
  name: string;
  code: string;
  description?: string;
  address: string;
  latitude?: number;
  longitude?: number;
  managerId?: number;
  managerName?: string;
  totalZones?: number;
  totalSpaces?: number;
  availableSpaces?: number;      // libres en zonas activas
  occupiedSpaces?: number;
  unavailableSpaces?: number;    // mantenimiento, fuera de servicio o zona no activa
  occupancyPercentage?: number;
  status: ParkingStatus;
  operational?: boolean;         // activo y no eliminado: recibe vehículos
  createdAt?: string;
  createdBy?: number;
  updatedAt?: string;
  updatedBy?: number;
  deletedAt?: string;            // solo en la papelera
  deletedBy?: number;
}

/** Crear un parqueo (el código no cambia después) */
export interface ParkingRequest {
  name: string;
  code: string;
  description?: string;
  address: string;
  latitude?: number;
  longitude?: number;
  managerId?: number;
}

/**
 * Editar un parqueo. Es un reemplazo completo: un responsable omitido se quita.
 * status vacío = no cambia.
 */
export interface ParkingUpdateRequest {
  name: string;
  description?: string;
  address: string;
  latitude?: number;
  longitude?: number;
  managerId?: number | null;
  status?: ParkingStatus;
}
