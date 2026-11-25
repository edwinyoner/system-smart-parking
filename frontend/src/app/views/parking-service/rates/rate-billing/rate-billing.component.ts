import { Component, DestroyRef, Input, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, debounceTime, filter, startWith, switchMap } from 'rxjs';

import { RateService } from '../../../../core/services/parking-service/rate.service';
import { RateSimulation } from '../../../../core/models/parking-service/rate.model';
import { BILLING_UNIT_PRESETS, formatMinutes } from '../../../../core/utils/rate.util';

/**
 * Reglas de cobro de una tarifa y simulación en vivo.
 *
 * Se usa dentro del formulario de crear y de editar. La simulación la calcula el backend con
 * el mismo método que cobra la salida de un vehículo, así lo que se ve es lo que se cobrará.
 */
@Component({
  selector: 'app-rate-billing',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './rate-billing.component.html',
  styleUrls: ['./rate-billing.component.css']
})
export class RateBillingComponent implements OnInit {

  /** Formulario padre con amount, billingUnitMinutes, minimumMinutes, graceMinutes y toleranceMinutes */
  @Input({ required: true }) form!: FormGroup;

  presets = BILLING_UNIT_PRESETS;
  simulation: RateSimulation | null = null;
  simulationError: string | null = null;
  formatMinutes = formatMinutes;

  private destroyRef = inject(DestroyRef);

  constructor(private rateService: RateService) {}

  ngOnInit(): void {
    this.form.valueChanges.pipe(
      startWith(null),
      debounceTime(400),
      filter(() => this.billingValid()),
      switchMap(() => {
        const v = this.form.getRawValue();
        return this.rateService.simulate({
          amount: Number(v.amount),
          billingUnitMinutes: Number(v.billingUnitMinutes),
          minimumMinutes: Number(v.minimumMinutes),
          graceMinutes: Number(v.graceMinutes),
          toleranceMinutes: Number(v.toleranceMinutes),
        }).pipe(catchError(error => {
          this.simulationError = error.error?.message || 'No se pudo calcular la simulación';
          this.simulation = null;
          return EMPTY;
        }));
      }),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(simulation => {
      this.simulation = simulation;
      this.simulationError = null;
    });
  }

  /** La simulación solo se pide con datos válidos */
  private billingValid(): boolean {
    const names = ['amount', 'billingUnitMinutes', 'minimumMinutes', 'graceMinutes', 'toleranceMinutes'];
    const valid = names.every(name => this.form.get(name)?.valid) && !this.form.errors?.['toleranceTooLarge'];
    if (!valid) this.simulation = null;
    return valid;
  }

  setUnit(minutes: number): void {
    // La tolerancia debe ser menor que la fracción (al minuto queda en 0)
    const tolerance = Number(this.form.get('toleranceMinutes')?.value) || 0;
    this.form.patchValue({
      billingUnitMinutes: minutes,
      toleranceMinutes: Math.min(tolerance, minutes - 1)
    });
    this.form.get('billingUnitMinutes')?.markAsDirty();
  }

  get unit(): number {
    return Number(this.form.get('billingUnitMinutes')?.value) || 0;
  }

  isInvalid(name: string): boolean {
    const control = this.form.get(name);
    return !!(control && control.invalid && (control.dirty || control.touched));
  }
}
