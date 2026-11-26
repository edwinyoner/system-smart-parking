// src/app/core/models/parking-service/space.model.ts

export type SpaceType = 'PARALLEL' | 'DIAGONAL' | 'PERPENDICULAR';
export type SpaceStatus = 'AVAILABLE' | 'OCCUPIED' | 'MAINTENANCE' | 'OUT_OF_SERVICE';

/**
 * Espacio de una zona. OCCUPIED solo lo ponen la entrada de vehículos o la detección
 * (cámara o sensor); a mano solo se elige libre, mantenimiento o fuera de servicio.
 */
export interface Space {
  id?: number;
  zoneId: number;
  zoneName?: string;
  zoneCode?: string;
  parkingId?: number;
  parkingName?: string;
  type: SpaceType;
  typeName?: string;
  code: string;
  description?: string;
  width?: number;
  length?: number;
  area?: number;
  hasSensor?: boolean;           // se deduce de sensorId
  sensorId?: string;
  hasCameraCoverage?: boolean;
  status?: SpaceStatus;
  receivesVehicles?: boolean;    // libre y con zona y parqueo activos
  createdAt?: string;
  createdBy?: number;
  updatedAt?: string;
  updatedBy?: number;
  deletedAt?: string;
  deletedBy?: number;
}

/** Crear un espacio (la zona y el código no cambian después) */
export interface SpaceRequest {
  zoneId: number;
  code: string;
  type: SpaceType;
  description?: string;
  width?: number;
  length?: number;
  sensorId?: string;
  hasCameraCoverage?: boolean;
}

/** Editar un espacio (status vacío = no cambia; nunca OCCUPIED) */
export interface SpaceUpdateRequest {
  type: SpaceType;
  description?: string;
  width?: number;
  length?: number;
  sensorId?: string;
  hasCameraCoverage?: boolean;
  status?: Exclude<SpaceStatus, 'OCCUPIED'>;
}
