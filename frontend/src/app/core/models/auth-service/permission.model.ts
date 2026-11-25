/**
 * Modelo de Permiso
 * Representa un permiso del sistema
 */
export interface Permission {
  id: number;
  name: string;
  description: string;
  status: boolean;
  module?: string; // users, roles, parking, rates, reports
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  updatedBy?: string;
}

/**
 * Filtros para búsqueda de permisos
 */
export interface PermissionFilters {
  search?: string;
  module?: string;
  status?: boolean;
  page?: number;
  size?: number;
  sortBy?: string;
  sortDirection?: 'asc' | 'desc';
}

/**
 * Permisos agrupados por módulo
 */
export interface PermissionGroup {
  module: string;          // Clave técnica: "users", "zones"...
  label?: string;          // Nombre para mostrar: "Usuarios"
  icon?: string;           // Clase de Font Awesome
  section?: string;        // Agrupación superior: "Seguridad y accesos"...
  permissions: Permission[];
}