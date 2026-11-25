/** Minúsculas y sin tildes, para comparar textos en filtros ("Configuración" → "configuracion") */
export function normalizeText(value: string | null | undefined): string {
  return (value ?? '').normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();
}
