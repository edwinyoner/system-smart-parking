/** Presentación de los roles del sistema */
const SYSTEM_ROLES: Record<string, { label: string; icon: string }> = {
  ADMIN: { label: 'Administrador', icon: 'fas fa-user-shield' },
  AUTORIDAD: { label: 'Autoridad', icon: 'fas fa-user-tie' },
  OPERADOR: { label: 'Operador', icon: 'fas fa-user-cog' },
};

/** Roles que siempre existen */
export const SYSTEM_ROLE_NAMES = Object.keys(SYSTEM_ROLES);

/** Nombre legible. Un rol creado desde la interfaz: JEFE_DE_ZONA → Jefe de zona */
export function getRoleLabel(name: string): string {
  const text = name.replace(/_/g, ' ').toLowerCase();
  return SYSTEM_ROLES[name]?.label ?? text.charAt(0).toUpperCase() + text.slice(1);
}

export function getRoleIconClass(name: string | null): string {
  return (name && SYSTEM_ROLES[name]?.icon) || 'fas fa-user';
}
