// src/app/core/models/parking-service/operator.model.ts

/** Usuario con el rol OPERADOR activo (GET /users/operators de auth-service) */
export interface Operator {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber?: string;
  status?: boolean;
}
