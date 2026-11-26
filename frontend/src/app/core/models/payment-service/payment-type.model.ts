/**
 * Tipo de pago (payment-service): cómo paga el conductor y quién confirma el pago.
 */
export type ConfirmationMode = 'MANUAL' | 'AUTOMATIC';
export type PaymentChannel = 'EXIT_SCREEN' | 'STREET_KIOSK' | 'MOBILE_APP' | 'OPERATOR';

export interface PaymentType {
  id?: number;
  code: string;
  name: string;
  description?: string;
  confirmationMode: ConfirmationMode;
  requiresReference: boolean;       // solo en los manuales: el operador anota el N.° de operación
  channels: PaymentChannel[];
  status: boolean;
  paymentsCount: number;            // pagos hechos con este tipo (con pagos no se elimina)
  createdAt?: string;
  createdBy?: number;
  updatedAt?: string;
  updatedBy?: number;
  deletedAt?: string;               // solo en la papelera
  deletedBy?: number;
}

export interface PaymentTypeRequest {
  code?: string;                    // solo al crear: no se puede cambiar
  name: string;
  description?: string;
  confirmationMode: ConfirmationMode;
  requiresReference: boolean;
  channels: PaymentChannel[];
  status?: boolean;
}
