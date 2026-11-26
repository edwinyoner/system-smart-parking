// src/app/core/models/parking-service/parking-shift-rate.model.ts

/**
 * Un turno del horario de un parqueo con su tarifa (vale para todas sus zonas).
 * usable = activo y con turno y tarifa activos: en ese horario el parqueo atiende.
 */
export interface ParkingShiftRate {
  id?: number;
  parkingId: number;
  shiftId: number;
  shiftName?: string;
  shiftCode?: string;
  startTime?: string;            // "HH:mm"
  endTime?: string;
  crossesMidnight?: boolean;
  shiftUsable?: boolean;
  rateId: number;
  rateName?: string;
  rateAmount?: number;
  rateCurrency?: string;
  rateUsable?: boolean;
  status: boolean;
  usable?: boolean;
  createdAt?: string;
  createdBy?: number;
  updatedAt?: string;
  updatedBy?: number;
}

/** Reemplaza todo el horario del parqueo (lista vacía = no atiende en ningún horario) */
export interface ConfigureParkingShiftRatesRequest {
  configurations: {
    shiftId: number;
    rateId: number;
    status?: boolean;
  }[];
}

/** ¿Atiende ahora? current es null si no atiende */
export interface ParkingScheduleStatus {
  parkingId: number;
  at: string;
  open: boolean;
  current: ParkingShiftRate | null;
}
