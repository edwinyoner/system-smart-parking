// src/app/core/models/parking-service/report.model.ts

/** Lo que pasó en un parqueo o una zona durante el día (calculado desde las estadías) */
export interface DaySummary {
  entries: number;
  exits: number;
  /** Vehículos distintos que estuvieron dentro (incluye los que venían de días anteriores) */
  vehicles: number;
  stays: number;
  /** Dentro al terminar el día, o ahora si es hoy */
  insideAtClose: number;
  /** Canceladas que habían entrado ese día (no cuentan en lo demás) */
  cancelled: number;
  averageStayMinutes: number | null;
  infractions: number;
}

export interface ZoneDay {
  zoneId: number;
  zoneName: string;
  zoneCode: string;
  totalSpaces: number;
  summary: DaySummary;
  peakHour: number | null;
  peakOccupied: number;
}

export interface ParkingDay {
  parkingId: number;
  parkingName: string;
  parkingCode: string;
  totalSpaces: number;
  summary: DaySummary;
  peakHour: number | null;
  peakOccupied: number;
  zones: ZoneDay[];
}

export interface DailyReport {
  date: string;
  /** Hasta qué hora hay datos (fin del día, o ahora si es hoy) */
  until: string;
  totals: DaySummary;
  parkings: ParkingDay[];
}

export interface HourSlot {
  hour: number;
  entries: number;
  exits: number;
  vehicles: number;
  occupiedAtStart: number;
  maxOccupied: number;
  /** La hora aún no empieza */
  future: boolean;
}

export interface OccupancyReport {
  date: string;
  until: string;
  parkingId: number;
  parkingName: string;
  zoneId: number | null;
  zoneName: string | null;
  capacity: number;
  hours: HourSlot[];
  peakHour: number | null;
  peakOccupied: number;
  entries: number;
  exits: number;
  vehicles: number;
  zones: { id: number; name: string; code: string }[];
}
