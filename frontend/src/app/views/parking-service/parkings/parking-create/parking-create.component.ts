import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { Parking } from '../../../../core/models/parking-service/parking.model';
import { ParkingShiftRate } from '../../../../core/models/parking-service/parking-shift-rate.model';
import { Step1InfoComponent } from '../steps/step1-info/step1-info.component';
import { PlannedZone, Step2ZonesComponent } from '../steps/step2-zones/step2-zones.component';
import { Step3SpacesComponent } from '../steps/step3-spaces/step3-spaces.component';
import { Step4ConfigComponent } from '../steps/step4-config/step4-config.component';
import { Step5OperatorsComponent } from '../steps/step5-operators/step5-operators.component';

interface StepConfig {
  number: number;
  label: string;
  icon: string;
}

/**
 * Asistente de 5 pasos para crear un parqueo completo.
 *
 * El parqueo se guarda en el paso 1: desde ahí cada paso guarda lo suyo. Si se sale a mitad,
 * lo guardado se conserva y se completa desde el detalle del parqueo. Los pasos 4 (horario)
 * y 5 (operadores) se pueden dejar para después; sin horario el parqueo no recibe vehículos.
 */
@Component({
  selector: 'app-parking-create',
  standalone: true,
  imports: [
    CommonModule,
    Step1InfoComponent,
    Step2ZonesComponent,
    Step3SpacesComponent,
    Step4ConfigComponent,
    Step5OperatorsComponent,
  ],
  templateUrl: './parking-create.component.html',
  styleUrl: './parking-create.component.css',
})
export class ParkingCreateComponent {

  currentStep = 1;
  readonly totalSteps = 5;

  parking: Parking | null = null;
  zones: PlannedZone[] = [];
  schedule: ParkingShiftRate[] = [];
  spacesCreated = false;
  operatorsAssigned = 0;
  /** Pasos que se dejaron para después */
  skipped = new Set<number>();

  readonly steps: StepConfig[] = [
    { number: 1, label: 'Información', icon: 'fa-info-circle' },
    { number: 2, label: 'Zonas',       icon: 'fa-map-marked-alt' },
    { number: 3, label: 'Espacios',    icon: 'fa-th' },
    { number: 4, label: 'Horario',     icon: 'fa-clock' },
    { number: 5, label: 'Operadores',  icon: 'fa-user-clock' },
  ];

  constructor(private router: Router) {}

  get progressValue(): number {
    return ((this.currentStep - 1) / (this.totalSteps - 1)) * 100;
  }

  isStepCompleted(step: number): boolean {
    return step < this.currentStep && !this.skipped.has(step);
  }

  isStepSkipped(step: number): boolean {
    return step < this.currentStep && this.skipped.has(step);
  }

  isStepActive(step: number): boolean {
    return step === this.currentStep;
  }

  // ========================= CALLBACKS DE CADA PASO =========================

  onParkingCreated(parking: Parking): void {
    this.parking = parking;
    this.currentStep = 2;
  }

  onZonesCreated(zones: PlannedZone[]): void {
    this.zones = zones;
    this.currentStep = 3;
  }

  onSpacesCreated(): void {
    this.spacesCreated = true;
    this.currentStep = 4;
  }

  onConfigSaved(schedule: ParkingShiftRate[]): void {
    this.schedule = schedule;
    if (schedule.length === 0) {
      this.skipped.add(4);
    }
    this.currentStep = 5;
  }

  /** @param assigned asignaciones guardadas (0 = se dejó para después) */
  onCompleted(assigned: number): void {
    this.operatorsAssigned = assigned;
    if (assigned === 0) {
      this.skipped.add(5);
    }
    this.finish();
  }

  // ========================= SALIR =========================

  /** Termina y abre el detalle del parqueo (o la lista si aún no se creó) */
  finish(): void {
    if (!this.parking?.id) {
      this.router.navigate(['/parkings']);
      return;
    }
    const pending: string[] = [];
    if (this.zones.length === 0) pending.push('sus zonas');
    else if (!this.spacesCreated) pending.push('sus espacios');
    if (this.schedule.length === 0) pending.push('su horario');
    if (this.operatorsAssigned === 0) pending.push('sus operadores');
    const message = `Parqueo "${this.parking.name}" creado` + (pending.length
      ? `. Falta configurar ${pending.join(' y ')}: hazlo desde esta pantalla.`
      : ' y listo para operar.');
    this.router.navigate(['/parkings', this.parking.id], { state: { successMessage: message } });
  }

  onCancel(): void {
    this.finish();
  }
}
