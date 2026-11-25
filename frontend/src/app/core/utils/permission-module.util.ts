import { Permission, PermissionGroup } from '../models/auth-service/permission.model';

/**
 * Catálogo de módulos de permisos.
 * El módulo se deduce del nombre del permiso ("users.create" → "users"),
 * porque el backend no envía un campo "module". El orden de este catálogo
 * es el orden en que se muestran los módulos en pantalla.
 */
interface ModuleInfo {
  label: string;
  icon: string;      // Clase de Font Awesome
  section: string;   // Agrupación superior
}

const MODULES: Record<string, ModuleInfo> = {
  // Seguridad
  users:            { label: 'Usuarios',             icon: 'fa-users',               section: 'Seguridad y accesos' },
  roles:            { label: 'Roles',                icon: 'fa-user-shield',         section: 'Seguridad y accesos' },
  permissions:      { label: 'Permisos',             icon: 'fa-key',                 section: 'Seguridad y accesos' },
  // Estacionamientos
  zones:            { label: 'Zonas',                icon: 'fa-map-marked-alt',      section: 'Estacionamientos' },
  'space-types':    { label: 'Tipos de espacio',     icon: 'fa-th-large',            section: 'Estacionamientos' },
  spaces:           { label: 'Espacios',             icon: 'fa-parking',             section: 'Estacionamientos' },
  shifts:           { label: 'Turnos',               icon: 'fa-clock',               section: 'Estacionamientos' },
  rates:            { label: 'Tarifas',              icon: 'fa-tags',                section: 'Estacionamientos' },
  // Operación diaria
  transactions:     { label: 'Transacciones',        icon: 'fa-exchange-alt',        section: 'Operación' },
  customers:        { label: 'Clientes',             icon: 'fa-user-tie',            section: 'Operación' },
  vehicles:         { label: 'Vehículos',            icon: 'fa-car',                 section: 'Operación' },
  'document-types': { label: 'Tipos de documento',   icon: 'fa-id-card',             section: 'Operación' },
  payments:         { label: 'Pagos',                icon: 'fa-cash-register',       section: 'Operación' },
  'payment-types':  { label: 'Tipos de pago',        icon: 'fa-money-check-alt',     section: 'Operación' },
  infractions:      { label: 'Infracciones',         icon: 'fa-exclamation-triangle', section: 'Operación' },
  // Reportes
  reports:          { label: 'Reportes',             icon: 'fa-chart-bar',           section: 'Reportes' },
};

const OTHER_SECTION = 'Otros';
const SECTION_ORDER = ['Seguridad y accesos', 'Estacionamientos', 'Operación', 'Reportes', OTHER_SECTION];
const MODULE_ORDER = Object.keys(MODULES);

/** Orden de las acciones dentro de un módulo (las demás van al final, por nombre) */
const ACTION_ORDER = ['read', 'view', 'create', 'update', 'delete', 'restore', 'activate'];

const ACTION_LABELS: Record<string, string> = {
  read: 'Ver', view: 'Ver', create: 'Crear', update: 'Editar', delete: 'Eliminar',
  restore: 'Restaurar', activate: 'Activar / desactivar', 'bulk-create': 'Creación masiva',
  cancel: 'Anular', resolve: 'Resolver', refund: 'Reembolsar', process: 'Procesar',
  export: 'Exportar', entry: 'Registrar entrada', exit: 'Registrar salida',
};

/** "users.create" → "users" */
export function getPermissionModule(permission: Permission): string {
  return permission.module || permission.name.split('.')[0] || 'general';
}

/** "users.create" → "Crear" */
export function getPermissionActionLabel(permission: Permission): string {
  const action = permission.name.split('.').slice(1).join('.');
  return ACTION_LABELS[action] ?? action.replace(/-/g, ' ');
}

/** Agrupa por módulo, en el orden del catálogo y con las acciones ordenadas */
export function groupPermissionsByModule(permissions: Permission[]): PermissionGroup[] {
  const byModule = new Map<string, Permission[]>();
  for (const permission of permissions) {
    const module = getPermissionModule(permission);
    byModule.set(module, [...(byModule.get(module) ?? []), permission]);
  }

  const rank = (list: string[], value: string) => {
    const i = list.indexOf(value);
    return i === -1 ? list.length : i;
  };

  return [...byModule.entries()]
    .map(([module, perms]): PermissionGroup => {
      const info = MODULES[module];
      return {
        module,
        label: info?.label ?? module.replace(/-/g, ' ').replace(/^./, c => c.toUpperCase()),
        icon: info?.icon ?? 'fa-folder-open',
        section: info?.section ?? OTHER_SECTION,
        permissions: [...perms].sort((a, b) => {
          const actionA = a.name.split('.').slice(1).join('.');
          const actionB = b.name.split('.').slice(1).join('.');
          return rank(ACTION_ORDER, actionA) - rank(ACTION_ORDER, actionB) || actionA.localeCompare(actionB);
        }),
      };
    })
    .sort((a, b) =>
      rank(SECTION_ORDER, a.section!) - rank(SECTION_ORDER, b.section!)
      || rank(MODULE_ORDER, a.module) - rank(MODULE_ORDER, b.module)
      || a.module.localeCompare(b.module));
}

/** Datos de un módulo para mostrar (también los que no están en el catálogo) */
export function getModuleInfo(module: string): { module: string; label: string; icon: string; section: string } {
  const info = MODULES[module];
  return {
    module,
    label: info?.label ?? module.replace(/-/g, ' ').replace(/^./, c => c.toUpperCase()),
    icon: info?.icon ?? 'fa-folder-open',
    section: info?.section ?? OTHER_SECTION,
  };
}

/** "users.create" → "create" */
export function getPermissionAction(permission: Permission): string {
  return permission.name.split('.').slice(1).join('.');
}

/**
 * Tipo de acción para filtrar: las 4 básicas (ver, crear, editar, eliminar)
 * y "other" para las propias de cada módulo (restaurar, anular, exportar...).
 */
export type ActionKind = 'read' | 'create' | 'update' | 'delete' | 'other';

export function getActionKind(permission: Permission): ActionKind {
  const action = getPermissionAction(permission);
  if (action === 'read' || action === 'view') return 'read';
  if (action === 'create' || action === 'update' || action === 'delete') return action;
  return 'other';
}

export const ACTION_KIND_OPTIONS: { value: ActionKind; label: string }[] = [
  { value: 'read', label: 'Ver' },
  { value: 'create', label: 'Crear' },
  { value: 'update', label: 'Editar' },
  { value: 'delete', label: 'Eliminar' },
  { value: 'other', label: 'Otras (restaurar, anular, exportar...)' },
];
