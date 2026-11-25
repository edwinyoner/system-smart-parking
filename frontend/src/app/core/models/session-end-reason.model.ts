/**
 * Mensajes según el motivo con que auth-service cerró la sesión.
 * Por la URL solo viaja el código: así nadie puede mostrar un texto propio en el login.
 */
const SESSION_END_MESSAGES: Record<string, string> = {
  ACCOUNT_DISABLED: 'Tu usuario fue desactivado. Comunícate con el administrador.',
  ACCOUNT_DELETED: 'Tu usuario fue dado de baja. Comunícate con el administrador.',
  PASSWORD_CHANGED: 'Tu contraseña fue cambiada. Inicia sesión con la nueva.',
  ROLE_REMOVED: 'Ya no tienes el rol con el que iniciaste sesión. Vuelve a entrar con otro.',
};

export function getSessionEndMessage(reason?: string): string {
  return (reason && SESSION_END_MESSAGES[reason])
    || 'Tu sesión ha expirado. Por favor, inicia sesión nuevamente.';
}
