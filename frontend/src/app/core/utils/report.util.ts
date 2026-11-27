// src/app/core/utils/report.util.ts

/**
 * Utilidades de reportes: formato, exportación a CSV y colores de gráficos.
 * El PDF se genera con la impresión del navegador ("Guardar como PDF"), que oculta el menú.
 */

/** "08:00" */
export function hourLabel(hour: number | null | undefined): string {
  return hour == null ? '-' : `${String(hour).padStart(2, '0')}:00`;
}

/** "08:00 - 08:59" */
export function hourRange(hour: number): string {
  return `${hourLabel(hour)} - ${String(hour).padStart(2, '0')}:59`;
}

/** 135 -> "2 h 15 min" */
export function minutesLabel(minutes: number | null | undefined): string {
  if (minutes == null) return '-';
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return h > 0 ? `${h} h ${m} min` : `${m} min`;
}

/** Porcentaje entero de a sobre b (0 si b es 0) */
export function percent(a: number, b: number): number {
  return b > 0 ? Math.round((a / b) * 100) : 0;
}

export type CsvCell = string | number | null | undefined;

/**
 * Descarga un CSV. Usa punto y coma y BOM UTF-8 para que Excel en español lo abra con sus
 * tildes y columnas. Los montos se escriben con punto decimal.
 */
export function downloadCsv(fileName: string, rows: CsvCell[][]): void {
  const escape = (v: CsvCell): string => {
    if (v == null) return '';
    const s = String(v);
    return /[;"\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  const content = '﻿' + rows.map(r => r.map(escape).join(';')).join('\r\n');
  const url = URL.createObjectURL(new Blob([content], { type: 'text/csv;charset=utf-8' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  a.click();
  URL.revokeObjectURL(url);
}

/**
 * Abre la impresión del navegador (desde ahí se guarda como PDF). En modo oscuro cambia un
 * momento al tema claro, para que el papel no salga con texto claro sobre blanco; los gráficos
 * se redibujan con ese tema y al cerrar la impresión se vuelve al tema anterior.
 */
export function printReport(): void {
  const html = document.documentElement;
  const previous = html.getAttribute('data-coreui-theme');
  if (previous !== 'dark') {
    window.print();
    return;
  }
  const restore = () => {
    html.setAttribute('data-coreui-theme', previous);
    window.removeEventListener('afterprint', restore);
  };
  window.addEventListener('afterprint', restore);
  html.setAttribute('data-coreui-theme', 'light');
  // Un instante para que los gráficos se redibujen con los colores claros
  setTimeout(() => window.print(), 150);
}

export function isDarkTheme(): boolean {
  return document.documentElement.getAttribute('data-coreui-theme') === 'dark'
    || document.body.getAttribute('data-coreui-theme') === 'dark';
}

/**
 * Colores de series validados (separación para daltonismo y contraste) por tema:
 * serie 1 azul, serie 2 verde azulado. En orden fijo, nunca rotados.
 */
export function seriesColors(): string[] {
  return isDarkTheme() ? ['#6E9FE6', '#3FAF8A'] : ['#2F6DB5', '#17A398'];
}

/** Colores de texto y rejilla del tema actual */
export function chartInk(): { text: string; muted: string; grid: string; surface: string } {
  const css = getComputedStyle(document.documentElement);
  const v = (name: string, fallback: string) => css.getPropertyValue(name).trim() || fallback;
  return {
    text: v('--cui-body-color', '#2B2F36'),
    muted: v('--cui-secondary-color', '#6c757d'),
    grid: v('--cui-border-color-translucent', 'rgba(0,0,0,0.1)'),
    surface: v('--cui-body-bg', '#ffffff')
  };
}

/** Lunes 01/12/2026 */
export function longDate(iso: string): string {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d).toLocaleDateString('es-PE', { weekday: 'long', day: '2-digit', month: '2-digit', year: 'numeric' });
}
