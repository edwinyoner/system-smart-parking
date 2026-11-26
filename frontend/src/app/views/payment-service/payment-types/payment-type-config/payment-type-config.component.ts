import { Component, DestroyRef, Input, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { ConfirmationMode, PaymentChannel } from '../../../../core/models/payment-service/payment-type.model';
import { CONFIRMATION_MODES, PAYMENT_CHANNELS } from '../../../../core/utils/payment-type.util';

/**
 * Cómo se confirma un tipo de pago y en qué canales se ofrece.
 * Se usa dentro del formulario de crear y de editar.
 */
@Component({
  selector: 'app-payment-type-config',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './payment-type-config.component.html',
  styleUrls: ['./payment-type-config.component.css']
})
export class PaymentTypeConfigComponent implements OnInit {

  /** Formulario padre con confirmationMode, requiresReference y channels */
  @Input({ required: true }) form!: FormGroup;

  modes = CONFIRMATION_MODES;
  channels = PAYMENT_CHANNELS;

  private destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    // Al pasar a manual solo queda el operador; el número de operación solo aplica a los manuales
    this.form.get('confirmationMode')?.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((mode: ConfirmationMode) => {
        if (mode === 'MANUAL') {
          this.form.patchValue({ channels: ['OPERATOR'] });
        } else {
          this.form.patchValue({ requiresReference: false });
        }
      });
  }

  get isManual(): boolean {
    return this.form.get('confirmationMode')?.value === 'MANUAL';
  }

  hasChannel(channel: PaymentChannel): boolean {
    return (this.form.get('channels')?.value ?? []).includes(channel);
  }

  /** En un tipo manual, solo el canal operador está disponible */
  isChannelDisabled(channel: PaymentChannel): boolean {
    return this.isManual && channel !== 'OPERATOR';
  }

  toggleChannel(channel: PaymentChannel): void {
    if (this.isChannelDisabled(channel)) return;
    const current: PaymentChannel[] = this.form.get('channels')?.value ?? [];
    const next = current.includes(channel) ? current.filter(c => c !== channel) : [...current, channel];
    this.form.patchValue({ channels: next });
    this.form.get('channels')?.markAsTouched();
  }

  get channelError(): string | null {
    if (!this.form.get('channels')?.touched) return null;
    if (this.form.errors?.['noChannels']) return 'Elige al menos un canal';
    if (this.form.errors?.['manualOnlyOperator']) return 'Un tipo manual solo se ofrece con operador';
    return null;
  }
}
