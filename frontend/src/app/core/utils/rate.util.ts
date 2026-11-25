import { AbstractControl, FormBuilder, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';

/**
 * Utilidades de tarifas. Las reglas son las mismas que valida el dominio Rate del backend.
 */

/** Fracciones de cobro habituales (minutos). Se puede escribir cualquier otra */
export const BILLING_UNIT_PRESETS = [
  { minutes: 60, label: 'Por hora o fracción' },
  { minutes: 30, label: 'Por media hora' },
  { minutes: 15, label: 'Cada 15 minutos' },
  { minutes: 1, label: 'Al minuto' },
];

/**
 * Controles de precio y reglas de cobro con los valores por defecto de todas las tarifas:
 * por hora o fracción, 5 min sin costo (entró por error) y 5 min de tolerancia.
 */
export function rateBillingControls(fb: FormBuilder) {
  return {
    amount: [null as number | null, [Validators.required, Validators.min(0.01), Validators.max(99999999.99), twoDecimals]],
    billingUnitMinutes: [60, [Validators.required, Validators.min(1), Validators.max(1440)]],
    minimumMinutes: [0, [Validators.required, Validators.min(0), Validators.max(1440)]],
    graceMinutes: [5, [Validators.required, Validators.min(0), Validators.max(120)]],
    toleranceMinutes: [5, [Validators.required, Validators.min(0)]],
  };
}

/** Como máximo 2 decimales */
function twoDecimals(control: AbstractControl): ValidationErrors | null {
  const value = control.value;
  if (value === null || value === '' || value === undefined) return null;
  return /^\d+(\.\d{1,2})?$/.test(String(value)) ? null : { decimals: true };
}

/** Validador de grupo: la tolerancia debe ser menor que la fracción */
export const toleranceValidator: ValidatorFn = (form: AbstractControl): ValidationErrors | null => {
  const unit = Number(form.get('billingUnitMinutes')?.value);
  const tolerance = Number(form.get('toleranceMinutes')?.value);
  return unit > 0 && tolerance >= unit ? { toleranceTooLarge: true } : null;
};

/** 75 -> "1 h 15 min"; 30 -> "30 min" */
export function formatMinutes(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (h === 0) return `${m} min`;
  return m === 0 ? `${h} h` : `${h} h ${m} min`;
}

/** Texto corto de las reglas: "Por hora o fracción · gracia 10 min" */
export function describeBilling(rate: { billingUnitMinutes: number; minimumMinutes: number;
  graceMinutes: number; toleranceMinutes: number }): string {
  const preset = BILLING_UNIT_PRESETS.find(p => p.minutes === rate.billingUnitMinutes);
  const parts = [preset ? preset.label : `Cada ${formatMinutes(rate.billingUnitMinutes)}`];
  if (rate.minimumMinutes > 0) parts.push(`mínimo ${formatMinutes(rate.minimumMinutes)}`);
  if (rate.graceMinutes > 0) parts.push(`sin costo antes de ${formatMinutes(rate.graceMinutes)}`);
  if (rate.toleranceMinutes > 0) parts.push(`tolerancia ${formatMinutes(rate.toleranceMinutes)}`);
  return parts.join(' · ');
}
