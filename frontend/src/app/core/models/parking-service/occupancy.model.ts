// src/app/core/models/parking-service/occupancy.model.ts
// Mapa de ocupación en tiempo real (GET /occupancy/parkings y /occupancy/parkings/{id})

export type SpaceStatusCode = 'AVAILABLE' | 'OCCUPIED' | 'MAINTENANCE' | 'OUT_OF_SERVICE';

export interface StatusCounts {
  total: number;
  available: number;
  occupied: number;
  maintenance: number;
  outOfService: number;
}

export interface OccupancyParkingSummary {
  id: number;
  name: string;
  code: string;
  status: string;
  operational: boolean;
  totalZones: number;
  totalSpaces: number;
  availableSpaces: number;     // libres en zonas activas
  occupiedSpaces: number;
  unavailableSpaces: number;
  occupancyPercentage: number;
}

/** Con un vehículo dentro: transactionId, placa y hora de entrada (solo con transactions.view) */
export interface OccupancySpace {
  id: number;
  code: string;
  type: string;
  status: SpaceStatusCode;
  hasSensor: boolean;
  transactionId: number | null;
  plateNumber: string | null;
  occupiedSince: string | null;
}

export interface OccupancyZone {
  id: number;
  code: string;
  name: string;
  status: string;
  operational: boolean;        // recibe vehículos (zona y parqueo activos)
  counts: StatusCounts;
  availableForEntry: number;
  spaces: OccupancySpace[];
}

export interface OccupancyParkingMap {
  id: number;
  name: string;
  code: string;
  address: string | null;
  status: string;
  operational: boolean;
  counts: StatusCounts;
  availableForEntry: number;
  showsVehicles: boolean;
  generatedAt: string;
  zones: OccupancyZone[];
}
