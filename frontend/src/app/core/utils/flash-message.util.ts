/**
 * Lee el mensaje de éxito que dejó la pantalla anterior y lo elimina.
 * El navegador conserva history.state al recargar: sin borrarlo, reaparecería.
 */
export function takeSuccessMessage(): string | null {
  const { successMessage, ...rest } = window.history.state ?? {};
  if (!successMessage) {
    return null;
  }

  window.history.replaceState(rest, '');
  return successMessage;
}
