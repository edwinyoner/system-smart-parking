import { AbstractControl } from '@angular/forms';

/**
 * Teléfono celular peruano: 9 dígitos que empiezan por 9 (ej. 987654321).
 * Se guarda sin prefijo +51. Debe coincidir con la validación del backend.
 */
export const PHONE_MAX_LENGTH = 9;
export const PHONE_PATTERN = /^9\d{8}$/;
export const PHONE_ERROR_MESSAGE = 'Debe tener 9 dígitos y empezar por 9. Ejemplo: 987654321';

/**
 * Convierte un teléfono guardado con otro formato (p. ej. "+51 987 654 321")
 * al formato actual, para que los datos antiguos no bloqueen el formulario.
 */
export function normalizePhone(value: string | null | undefined): string {
  let digits = (value ?? '').replace(/\D/g, '');
  if (digits.length === 11 && digits.startsWith('51')) {
    digits = digits.slice(2);
  }
  return digits.slice(0, PHONE_MAX_LENGTH);
}

/**
 * Úsalo en (input) del campo: elimina al instante todo lo que no sea número
 * (también al pegar texto) y corta en 9 dígitos.
 */
export function keepPhoneDigits(event: Event, control: AbstractControl | null): void {
  const input = event.target as HTMLInputElement;
  const clean = input.value.replace(/\D/g, '').slice(0, PHONE_MAX_LENGTH);
  if (clean !== input.value) {
    input.value = clean;
    control?.setValue(clean);
  }
}
