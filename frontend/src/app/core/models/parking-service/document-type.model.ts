/**
 * Tipo de documento de identidad del cliente (DNI, CE, RUC, pasaporte...).
 * Sus reglas validan el número en la entrada, la salida y al registrar clientes.
 */
export interface DocumentType {
  id?: number;
  code: string;
  name: string;
  description?: string;
  sunatCode?: string;          // Catálogo N.° 06 de SUNAT, para el comprobante
  minLength: number;
  maxLength: number;
  numericOnly: boolean;
  lengthLabel?: string;        // "8 dígitos" o "entre 9 y 12 caracteres"
  status: boolean;
  customersCount?: number;     // clientes con este tipo
  transactionsCount?: number;  // transacciones que lo usaron
  inUse?: boolean;             // en uso: no se puede eliminar
  createdAt?: string;
  createdBy?: number;
  updatedAt?: string;
  updatedBy?: number;
  deletedAt?: string;          // solo en la papelera
  deletedBy?: number;
}

export interface DocumentTypeRequest {
  code?: string;               // solo al crear: no se puede cambiar
  name: string;
  description?: string;
  sunatCode?: string;
  minLength: number;
  maxLength: number;
  numericOnly: boolean;
  status?: boolean;
}
