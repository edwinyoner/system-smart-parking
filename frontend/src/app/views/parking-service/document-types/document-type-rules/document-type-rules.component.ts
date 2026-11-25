import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';

import {
  DOCUMENT_RULE_PRESETS, MAX_DOCUMENT_LENGTH, describeLength, documentNumberError, normalizeDocumentNumber
} from '../../../../core/utils/document-type.util';

/**
 * Reglas del número de documento (longitud, solo dígitos) y código SUNAT, con un campo para
 * probar un número. Se usa dentro del formulario de crear y de editar.
 */
@Component({
  selector: 'app-document-type-rules',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  templateUrl: './document-type-rules.component.html',
  styleUrls: ['./document-type-rules.component.css']
})
export class DocumentTypeRulesComponent {

  /** Formulario padre con code, sunatCode, minLength, maxLength y numericOnly */
  @Input({ required: true }) form!: FormGroup;

  presets = DOCUMENT_RULE_PRESETS;
  maxLength = MAX_DOCUMENT_LENGTH;
  sample = '';

  applyPreset(preset: typeof DOCUMENT_RULE_PRESETS[number]): void {
    this.form.patchValue({
      minLength: preset.minLength,
      maxLength: preset.maxLength,
      numericOnly: preset.numericOnly,
      sunatCode: preset.sunatCode,
    });
    this.form.markAsDirty();
  }

  isPreset(preset: typeof DOCUMENT_RULE_PRESETS[number]): boolean {
    const v = this.form.getRawValue();
    return Number(v.minLength) === preset.minLength && Number(v.maxLength) === preset.maxLength
      && !!v.numericOnly === preset.numericOnly;
  }

  get rulesValid(): boolean {
    return ['minLength', 'maxLength'].every(n => this.form.get(n)?.valid) && !this.form.errors?.['lengthRange'];
  }

  get lengthLabel(): string {
    const v = this.form.getRawValue();
    return describeLength({ minLength: Number(v.minLength), maxLength: Number(v.maxLength), numericOnly: !!v.numericOnly });
  }

  /** Resultado de probar el número escrito con las reglas actuales */
  get sampleResult(): { ok: boolean; text: string } | null {
    if (!this.sample.trim() || !this.rulesValid) return null;
    const v = this.form.getRawValue();
    const error = documentNumberError({
      code: (v.code || '').toUpperCase(),
      minLength: Number(v.minLength),
      maxLength: Number(v.maxLength),
      numericOnly: !!v.numericOnly,
    }, this.sample);
    return error
      ? { ok: false, text: error }
      : { ok: true, text: `Válido. Se guardará como ${normalizeDocumentNumber(this.sample)}` };
  }

  isInvalid(name: string): boolean {
    const control = this.form.get(name);
    return !!(control && control.invalid && (control.dirty || control.touched));
  }
}
