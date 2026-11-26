// src/app/views/parking-service/transactions/shared/stay-action-modal.component.ts
import { Component, DestroyRef, EventEmitter, Input, OnChanges, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable } from 'rxjs';
import {
  ModalComponent,
  ModalHeaderComponent,
  ModalTitleDirective,
  ModalBodyComponent,
  ModalFooterComponent,
  ButtonCloseDirective,
  ButtonDirective,
  SpinnerComponent,
  AlertComponent
} from '@coreui/angular';

import { TransactionService } from '../../../../core/services/parking-service/transaction.service';
import { SpaceService } from '../../../../core/services/parking-service/space.service';
import { Space } from '../../../../core/models/parking-service/space.model';
import { apiError } from '../../../../core/utils/parking.util';

/** Lo mínimo de una estadía en curso que necesita el modal */
export interface StayRef {
  id: number;
  plateNumber: string;
  zoneId: number;
  zoneName: string;
  spaceCode: string;
  paidAmount?: number;
}

export type StayAction = 'space' | 'cancel';

/**
 * Acciones sobre una estadía en curso, compartidas por "Vehículos dentro" y el detalle:
 * - space: cambiar al espacio donde de verdad estacionó (libre y de la misma zona).
 * - cancel: anular una estadía registrada por error (con motivo; libera el espacio).
 *   El backend rechaza la cancelación si ya tiene pagos: se avisa antes.
 */
@Component({
  selector: 'app-stay-action-modal',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ModalComponent,
    ModalHeaderComponent,
    ModalTitleDirective,
    ModalBodyComponent,
    ModalFooterComponent,
    ButtonCloseDirective,
    ButtonDirective,
    SpinnerComponent,
    AlertComponent
  ],
  template: `
    <c-modal [visible]="!!action && !!stay" (visibleChange)="!$event && close()" alignment="center">
      @if (stay) {
        <c-modal-header>
          <h5 cModalTitle>
            @if (action === 'space') {
              <i class="fa-solid fa-arrows-left-right me-2"></i>Cambiar de espacio
            } @else {
              <i class="fa-solid fa-ban me-2"></i>Cancelar estadía
            }
          </h5>
          <button cButtonClose (click)="close()"></button>
        </c-modal-header>
        <c-modal-body>
          <p class="mb-3">
            <strong>{{ stay.plateNumber }}</strong> en {{ stay.zoneName }}, espacio {{ stay.spaceCode }}.
          </p>

          @if (error) {
            <c-alert color="danger" class="py-2">{{ error }}</c-alert>
          }

          @if (action === 'space') {
            <label class="form-label" for="newSpace">Espacio donde estacionó</label>
            @if (loadingSpaces) {
              <div><c-spinner size="sm"></c-spinner></div>
            } @else if (spaces.length === 0) {
              <p class="text-body-secondary mb-0">No hay otros espacios libres en esta zona.</p>
            } @else {
              <select id="newSpace" class="form-select" [(ngModel)]="spaceId">
                <option [ngValue]="null">Selecciona</option>
                @for (s of spaces; track s.id) {
                  <option [ngValue]="s.id">{{ s.code }}</option>
                }
              </select>
              <small class="text-body-secondary">Solo espacios libres de la misma zona</small>
            }
          } @else {
            @if ((stay.paidAmount ?? 0) > 0) {
              <c-alert color="warning" class="py-2">
                Esta estadía ya tiene pagos: no se puede cancelar. Registra la salida o devuelve el pago primero.
              </c-alert>
            } @else {
              <p class="text-body-secondary">
                Úsalo solo si se registró por error (placa equivocada, vehículo que no entró).
                El espacio queda libre y la estadía no se cobra.
              </p>
              <label class="form-label" for="reason">Motivo <span class="text-danger">*</span></label>
              <textarea id="reason" class="form-control" rows="3" maxlength="500" [(ngModel)]="reason"
                        placeholder="Ej.: la cámara leyó mal la placa"></textarea>
            }
          }
        </c-modal-body>
        <c-modal-footer>
          <button cButton color="secondary" variant="outline" (click)="close()" [disabled]="saving">Volver</button>
          @if (action === 'space') {
            <button cButton color="warning" (click)="save()" [disabled]="!spaceId || saving">
              @if (saving) { <c-spinner size="sm" class="me-1"></c-spinner> }
              Cambiar espacio
            </button>
          } @else if ((stay.paidAmount ?? 0) === 0) {
            <button cButton color="danger" (click)="save()" [disabled]="!reason.trim() || saving">
              @if (saving) { <c-spinner size="sm" class="me-1"></c-spinner> }
              Cancelar estadía
            </button>
          }
        </c-modal-footer>
      }
    </c-modal>
  `
})
export class StayActionModalComponent implements OnChanges {

  private readonly transactions = inject(TransactionService);
  private readonly spaceService = inject(SpaceService);
  private readonly destroyRef = inject(DestroyRef);

  @Input() stay: StayRef | null = null;
  @Input() action: StayAction | null = null;
  /** Mensaje de éxito para mostrar en la vista que abrió el modal */
  @Output() done = new EventEmitter<string>();
  @Output() closed = new EventEmitter<void>();

  spaces: Space[] = [];
  spaceId: number | null = null;
  reason = '';
  loadingSpaces = false;
  saving = false;
  error = '';

  ngOnChanges(): void {
    this.error = '';
    this.spaceId = null;
    this.reason = '';
    this.spaces = [];
    if (this.action === 'space' && this.stay) {
      this.loadingSpaces = true;
      this.spaceService.getAvailableByZoneId(this.stay.zoneId)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: spaces => {
            this.spaces = spaces;
            this.loadingSpaces = false;
          },
          error: err => {
            this.error = apiError(err, 'No se pudieron cargar los espacios libres');
            this.loadingSpaces = false;
          }
        });
    }
  }

  save(): void {
    if (!this.stay || this.saving) return;
    const stay = this.stay;
    this.saving = true;
    this.error = '';
    // Solo importa si salió bien: la vista que abrió el modal recarga la estadía
    const request: Observable<unknown> = this.action === 'space'
      ? this.transactions.reassignSpace(stay.id, this.spaceId!)
      : this.transactions.cancel(stay.id, this.reason.trim());
    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.saving = false;
        const code = this.spaces.find(s => s.id === this.spaceId)?.code;
        this.done.emit(this.action === 'space'
          ? `${stay.plateNumber} ahora está en el espacio ${code ?? ''}`.trim()
          : `Estadía de ${stay.plateNumber} cancelada; el espacio ${stay.spaceCode} quedó libre`);
        this.close();
      },
      error: err => {
        this.saving = false;
        this.error = apiError(err, this.action === 'space' ? 'No se pudo cambiar el espacio' : 'No se pudo cancelar');
      }
    });
  }

  close(): void {
    this.closed.emit();
  }
}
