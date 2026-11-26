// src/app/core/models/parking-service/zone.model.ts

export type ZoneStatus = 'ACTIVE' | 'INACTIVE' | 'MAINTENANCE' | 'OUT_OF_SERVICE';

/**
 * Zona de un parqueo. Su capacidad se calcula desde sus espacios; "tiene cámara"
 * se deduce de la lista de cámaras.
 */
export interface Zone {
  id?: number;
  parkingId: number;
  parkingName?: string;
  parkingCode?: string;
  name: string;
  code: string;
  address?: string;
  description?: string;
  totalSpaces?: number;
  availableSpaces?: number;      // libres, solo si la zona está activa
  occupiedSpaces?: number;
  unavailableSpaces?: number;
  occupancyPercentage?: number;
  latitude?: number;
  longitude?: number;
  hasCamera?: boolean;
  cameraIds?: string[];
  cameraCount?: number;
  status?: ZoneStatus;
  operational?: boolean;
  createdAt?: string;
  createdBy?: number;
  updatedAt?: string;
  updatedBy?: number;
  deletedAt?: string;
  deletedBy?: number;
}

/** Crear una zona: el parqueo y el código no cambian después */
export interface ZoneRequest {
  parkingId: number;
  name: string;
  code: string;
  address?: string;              // vacío = la dirección del parqueo
  description?: string;
  latitude?: number;
  longitude?: number;
  cameraIds?: string[];
}

/** Editar una zona (status vacío = no cambia) */
export interface ZoneUpdateRequest {
  name: string;
  address?: string;
  description?: string;
  latitude?: number;
  longitude?: number;
  cameraIds?: string[];
  status?: ZoneStatus;
}
