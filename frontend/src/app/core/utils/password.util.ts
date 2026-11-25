const UPPER = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
const LOWER = 'abcdefghijklmnopqrstuvwxyz';
const DIGITS = '0123456789';
const SPECIAL = '@$!%*?&';

/** Entero aleatorio en [0, max) con el generador criptográfico del navegador */
function randomInt(max: number): number {
  const limit = Math.floor(0x100000000 / max) * max; // Evita sesgo por módulo
  const buffer = new Uint32Array(1);
  do {
    crypto.getRandomValues(buffer);
  } while (buffer[0] >= limit);
  return buffer[0] % max;
}

const pick = (chars: string): string => chars[randomInt(chars.length)];

/** Genera una contraseña con al menos una mayúscula, minúscula, número y símbolo */
export function generatePassword(length = 12): string {
  const chars = [pick(UPPER), pick(LOWER), pick(DIGITS), pick(SPECIAL)];
  while (chars.length < length) {
    chars.push(pick(UPPER + LOWER + DIGITS + SPECIAL));
  }

  // Mezcla Fisher-Yates
  for (let i = chars.length - 1; i > 0; i--) {
    const j = randomInt(i + 1);
    [chars[i], chars[j]] = [chars[j], chars[i]];
  }
  return chars.join('');
}

/** Oculta la contraseña y deja visibles solo los 3 últimos caracteres */
export function maskPassword(password: string): string {
  return '•'.repeat(Math.max(password.length - 3, 0)) + password.slice(-3);
}
