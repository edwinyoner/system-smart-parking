import { Role } from './role.model';

/**
 * Modelo de Usuario
 * Representa un usuario del sistema Smart Parking
 */
export interface User {
  id: number;
  firstName: string;
  lastName: string;
  fullName?: string;
  email: string;
  phoneNumber?: string;
  profilePicture?: string;
  emailVerified: boolean;
  status: boolean;
  roles: Role[];
  createdAt: string;
  updatedAt: string;
  deletedAt?: string | null;   // Solo viene con valor en la papelera
  createdBy?: string;
  updatedBy?: string;
}

/**
 * Respuesta de GET /users/me: además de los datos, los permisos vigentes del rol activo
 * (leídos de la BD en cada consulta, no del token).
 */
export interface CurrentUserResponse extends User {
  activeRole: string | null;
  activePermissions: string[];
}

/**
 * DTO para crear un nuevo usuario
 * Backend espera: roles como string[] (nombres de roles)
 */
export interface CreateUserRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  phoneNumber?: string;
  roles: string[]; // CAMBIO: string[] en lugar de roleIds: number[]
  captchaToken?: string;
}

/**
 * DTO para actualizar un usuario existente
 * Backend espera: roles como string[] (nombres de roles)
 */
export interface UpdateUserRequest {
  firstName: string;
  lastName: string;
  email?: string;      // Si cambia, el usuario debe verificarlo otra vez
  phoneNumber?: string;
  status: boolean;
  roles: string[]; // CAMBIO: string[] en lugar de roleIds: number[]
}

/**
 * Filtros para búsqueda de usuarios
 */
export interface UserFilters {
  search?: string;
  roleId?: number;
  status?: boolean;
  emailVerified?: boolean;
  deleted?: boolean;           // true = papelera (exige users.restore)
  page?: number;
  size?: number;
  sortBy?: string;
  sortDirection?: 'asc' | 'desc';
}