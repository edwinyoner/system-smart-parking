import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { ConfirmationMode, PaymentChannel } from '../models/payment-service/payment-type.model';

/**
 * Textos y reglas de los tipos de pago. Las reglas son las mismas que valida el dominio
 * PaymentType de payment-service, que es quien decide.
 */

export const CONFIRMATION_MODES: { value: ConfirmationMode; label: string; help: string }[] = [
  { value: 'AUTOMATIC', label: 'Automática', help: 'La pasarela confirma el pago (QR). No hace falta operador.' },
  { value: 'MANUAL', label: 'Manual', help: 'Un operador confirma que recibió el pago (efectivo).' },
];

export const PAYMENT_CHANNELS: { value: PaymentChannel; label: string; icon: string }[] = [
  { value: 'EXIT_SCREEN', label: 'Pantalla de salida', icon: 'fa-display' },
  { value: 'STREET_KIOSK', label: 'Kiosco de calle', icon: 'fa-tablet-screen-button' },
  { value: 'MOBILE_APP', label: 'App móvil', icon: 'fa-mobile-screen' },
  { value: 'OPERATOR', label: 'Operador', icon: 'fa-user-tie' },
];

export function confirmationLabel(mode: ConfirmationMode): string {
  return CONFIRMATION_MODES.find(m => m.value === mode)?.label ?? mode;
}

export function channelLabel(channel: PaymentChannel): string {
  return PAYMENT_CHANNELS.find(c => c.value === channel)?.label ?? channel;
}

/**
 * Validador de grupo:
 * - al menos un canal;
 * - un tipo manual solo se ofrece con operador (la pantalla, el kiosco y la app no confirman efectivo).
 */
export const paymentChannelsValidator: ValidatorFn = (form: AbstractControl): ValidationErrors | null => {
  const channels: PaymentChannel[] = form.get('channels')?.value ?? [];
  if (channels.length === 0) return { noChannels: true };
  const mode: ConfirmationMode = form.get('confirmationMode')?.value;
  if (mode === 'MANUAL' && channels.some(c => c !== 'OPERATOR')) return { manualOnlyOperator: true };
  return null;
};
