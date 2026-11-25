/**
 * Tarifa de estacionamiento: precio por hora (siempre en soles) y reglas de cobro.
 * El cálculo lo hace el backend (Rate.calculateCost); aquí solo se muestran los datos.
 */
export interface Rate {
  id?: number;
  name: string;
  description?: string;
  amount: number;                 // precio por hora
  currency: string;               // siempre "PEN"
  billingUnitMinutes: number;     // fracción de cobro: 60 = por hora o fracción
  minimumMinutes: number;         // tiempo mínimo cobrado
  graceMinutes: number;           // si sale dentro de este tiempo no paga
  toleranceMinutes: number;       // cortesía antes de la fracción siguiente
  fractionAmount?: number;        // precio de una fracción
  status: boolean;
  usedByParkings?: string[];      // parqueos que la usan
  createdAt?: string;
  createdBy?: number;
  updatedAt?: string;
  updatedBy?: number;
  deletedAt?: string;             // solo en la papelera
  deletedBy?: number;
}

/** Datos para crear o actualizar una tarifa (la moneda la fija el backend) */
export interface RateRequest {
  name: string;
  description?: string;
  amount: number;
  billingUnitMinutes: number;
  minimumMinutes: number;
  graceMinutes: number;
  toleranceMinutes: number;
  status?: boolean;
}

/** Simulación de cobro calculada por el backend */
export interface RateSimulation {
  fractionAmount: number;
  rows: { minutes: number; fractions: number; amount: number }[];
}
