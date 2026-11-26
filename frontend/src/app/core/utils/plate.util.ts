// src/app/core/utils/plate.util.ts
// Misma regla que PlateNumber del backend: placa peruana ABC-123 (antigua) o A1B-234 (nueva),
// siempre guardada con guion. Así el formulario avisa antes de enviar.

const OLD_FORMAT = /^[A-Z]{3}[0-9]{3}$/;
const NEW_FORMAT = /^[A-Z][0-9][A-Z][0-9]{3}$/;

/** Solo letras y números en mayúsculas ("abc 123" -> "ABC123") */
export function cleanPlate(raw: string | null | undefined): string {
  return (raw ?? '').toUpperCase().replace(/[^A-Z0-9]/g, '');
}

export function isValidPlate(raw: string | null | undefined): boolean {
  const c = cleanPlate(raw);
  return OLD_FORMAT.test(c) || NEW_FORMAT.test(c);
}

/** "abc123" -> "ABC-123"; si no es válida, devuelve el texto limpio sin guion */
export function normalizePlate(raw: string | null | undefined): string {
  const c = cleanPlate(raw);
  return isValidPlate(c) ? `${c.slice(0, 3)}-${c.slice(3)}` : c;
}

/** Validador para formularios reactivos: { plate: true } si no tiene formato peruano */
export function plateValidator(control: { value: unknown }): { plate: true } | null {
  const value = typeof control.value === 'string' ? control.value : '';
  return !value || isValidPlate(value) ? null : { plate: true };
}

/** Confianza 0-1 como porcentaje para mostrar ("87 %") */
export function confidencePercent(confidence: number | null | undefined): string {
  return confidence == null ? '-' : `${Math.round(confidence * 100)} %`;
}

/** Umbral del backend (parking.ai.min-plate-confidence): por debajo, la lectura se revisa */
export const MIN_PLATE_CONFIDENCE = 0.85;
