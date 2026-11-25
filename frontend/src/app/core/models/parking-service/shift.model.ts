// src/app/core/models/parking-service/shift.model.ts
export interface Shift {
  id?: number;
  name: string;
  code: string;
  description?: string;
  startTime: string;  // "HH:mm"
  endTime: string;    // "HH:mm"
  status: boolean;
  crossesMidnight?: boolean;   // termina al día siguiente (20:00 - 05:59)
  durationHours?: number;      // contando el minuto final (06:00 - 13:59 = 8)
  usedByParkings?: string[];   // parqueos que lo usan en sus tarifas
  createdAt?: string;
  createdBy?: number;
  updatedAt?: string;
  updatedBy?: number;
}

export interface ShiftRequest {
  name: string;
  code: string;
  description?: string;
  startTime: string;
  endTime: string;
  status?: boolean;
}