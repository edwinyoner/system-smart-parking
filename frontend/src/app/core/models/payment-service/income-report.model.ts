// src/app/core/models/payment-service/income-report.model.ts

/** Ingresos de un rango: pagos cobrados por el día en que se cobraron, menos lo devuelto */
export interface IncomeReport {
  from: string;
  to: string;
  currency: string;
  totals: {
    payments: number;
    gross: number;
    refunded: number;
    net: number;
    stays: number;
    fines: number;
  };
  /** Un elemento por día del rango, aunque no haya pagos */
  byDay: { date: string; payments: number; gross: number; refunded: number; net: number }[];
  byPaymentType: { code: string; name: string | null; payments: number; net: number }[];
  /** parkingId null: pagos antiguos sin parqueo */
  byParking: { parkingId: number | null; parkingName: string | null; payments: number; stays: number; fines: number; net: number }[];
}
