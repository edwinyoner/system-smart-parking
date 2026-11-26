import { AbstractControl, FormBuilder, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';

/**
 * Reglas del número de documento. Son las mismas que valida el dominio DocumentType del backend,
 * que es quien decide; aquí sirven para avisar antes de enviar.
 */
export interface DocumentNumberRules {
  code: string;
  minLength: number;
  maxLength: number;
  numericOnly: boolean;
}

export const MAX_DOCUMENT_LENGTH = 20;

/** Formatos habituales en el Perú. Se puede escribir cualquier otro */
export const DOCUMENT_RULE_PRESETS = [
  { label: 'DNI: 8 dígitos', minLength: 8, maxLength: 8, numericOnly: true, sunatCode: '1' },
  { label: 'CE: 9 a 12 caracteres', minLength: 9, maxLength: 12, numericOnly: false, sunatCode: '4' },
  { label: 'Pasaporte: 6 a 12 caracteres', minLength: 6, maxLength: 12, numericOnly: false, sunatCode: '7' },
];

/** Controles de las reglas del número, con los valores por defecto del backend */
export function documentRuleControls(fb: FormBuilder) {
  return {
    sunatCode: ['', [Validators.pattern(/^[A-Za-z0-9]{0,2}$/)]],
    minLength: [1, [Validators.required, Validators.min(1), Validators.max(MAX_DOCUMENT_LENGTH)]],
    maxLength: [MAX_DOCUMENT_LENGTH, [Validators.required, Validators.min(1), Validators.max(MAX_DOCUMENT_LENGTH)]],
    numericOnly: [false],
  };
}

/** Validador de grupo: la longitud mínima no puede superar la máxima */
export const lengthRangeValidator: ValidatorFn = (form: AbstractControl): ValidationErrors | null => {
  const min = Number(form.get('minLength')?.value);
  const max = Number(form.get('maxLength')?.value);
  return min > 0 && max > 0 && min > max ? { lengthRange: true } : null;
};

/** Sin espacios ni guiones y en mayúsculas, como lo guarda el backend */
export function normalizeDocumentNumber(value: string): string {
  return (value ?? '').trim().toUpperCase().replace(/[\s-]/g, '');
}

/** "8 dígitos" o "entre 9 y 12 caracteres" */
export function describeLength(rules: Pick<DocumentNumberRules, 'minLength' | 'maxLength' | 'numericOnly'>): string {
  const unit = rules.numericOnly ? 'dígitos' : 'caracteres';
  return rules.minLength === rules.maxLength
    ? `${rules.minLength} ${unit}`
    : `entre ${rules.minLength} y ${rules.maxLength} ${unit}`;
}

/** Mensaje de error si el número no cumple las reglas; null si es válido */
export function documentNumberError(rules: DocumentNumberRules, value: string): string | null {
  const number = normalizeDocumentNumber(value);
  const code = rules.code || 'documento';
  if (!number) return `El número de ${code} es obligatorio`;
  if (rules.numericOnly && !/^\d+$/.test(number)) return `El ${code} solo admite dígitos`;
  if (!rules.numericOnly && !/^[A-Z0-9]+$/.test(number)) return `El ${code} solo admite letras y dígitos`;
  if (number.length < rules.minLength || number.length > rules.maxLength) {
    return `El ${code} debe tener ${describeLength(rules)}`;
  }
  return null;
}
