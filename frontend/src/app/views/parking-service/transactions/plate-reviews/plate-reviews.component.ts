// src/app/views/parking-service/transactions/plate-reviews/plate-reviews.component.ts
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Subject, catchError, exhaustMap, merge, switchMap } from 'rxjs';
import {
  CardComponent,
  CardBodyComponent,
  ButtonDirective,
  SpinnerComponent,
  AlertComponent,
  BadgeComponent
} from '@coreui/angular';

import { PlateReviewService } from '../../../../core/services/parking-service/plate-review.service';
import { ParkingService } from '../../../../core/services/parking-service/parking.service';
import { DocumentTypeService } from '../../../../core/services/parking-service/document-type.service';
import { PermissionService } from '../../../../core/services/auth-service/permission.service';
import { PlateReview, PlateReviewStatus } from '../../../../core/models/parking-service/plate-review.model';
import { Parking } from '../../../../core/models/parking-service/parking.model';
import { DocumentType } from '../../../../core/models/parking-service/document-type.model';
import { ParkingPagedResponse } from '../../../../core/models/pagination.model';
import { liveRefresh } from '../../../../core/utils/live-refresh.util';
import { MIN_PLATE_CONFIDENCE, confidencePercent, isValidPlate, normalizePlate } from '../../../../core/utils/plate.util';
import { documentNumberError, normalizeDocumentNumber } from '../../../../core/utils/document-type.util';
import { apiError } from '../../../../core/utils/parking.util';

/** Lo que el operador escribe en cada tarjeta: sobrevive al refresco en vivo */
interface ReviewDraft {
  plate: string;
  documentTypeId: number | null;
  documentNumber: string;
  discarding: boolean;
  reason: string;
  saving: boolean;
  error: string;
}

type Tab = 'PENDING' | 'RESOLVED';

/**
 * "Por revisar": lecturas de las cámaras que no se registraron solas (confianza menor a 85 %,
 * placa sin formato válido, parqueo que exige documento o salida sin estadía en ese parqueo).
 * El operador mira la foto, confirma o corrige la placa (y el documento si el parqueo lo exige)
 * y el sistema registra la entrada o la salida; o la descarta con un motivo.
 * Las pendientes llegan de la más antigua a la más nueva y se actualizan cada 5 s.
 */
@Component({
  selector: 'app-plate-reviews',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    CardComponent,
    CardBodyComponent,
    ButtonDirective,
    SpinnerComponent,
    AlertComponent,
    BadgeComponent
  ],
  templateUrl: './plate-reviews.component.html',
  styleUrls: ['./plate-reviews.component.css']
})
export class PlateReviewsComponent implements OnInit {

  private readonly reviews = inject(PlateReviewService);
  private readonly parkingService = inject(ParkingService);
  private readonly documentTypeService = inject(DocumentTypeService);
  private readonly permissions = inject(PermissionService);
  private readonly destroyRef = inject(DestroyRef);

  tab: Tab = 'PENDING';
  resolvedStatus: PlateReviewStatus | 'ALL' = 'ALL';
  parkingId: number | null = null;
  page = 0;

  result: ParkingPagedResponse<PlateReview> | null = null;
  parkings: Parking[] = [];
  documentTypes: DocumentType[] = [];
  drafts = new Map<number, ReviewDraft>();

  loading = true;
  errorMessage = '';
  successMessage = '';
  successLink: number | null = null;
  lastUpdate: Date | null = null;

  readonly canEntry = this.permissions.hasPermission('transactions.entry');
  readonly canExit = this.permissions.hasPermission('transactions.exit');
  readonly confidencePercent = confidencePercent;
  readonly minConfidence = MIN_PLATE_CONFIDENCE;

  private readonly reload$ = new Subject<boolean>();

  ngOnInit(): void {
    this.parkingService.getActive()
      .pipe(catchError(() => EMPTY), takeUntilDestroyed(this.destroyRef))
      .subscribe(p => (this.parkings = p));
    this.documentTypeService.getActive()
      .pipe(catchError(() => EMPTY), takeUntilDestroyed(this.destroyRef))
      .subscribe(d => (this.documentTypes = d));

    merge(
      this.reload$.pipe(switchMap(silent => this.fetch(silent))),
      liveRefresh(5000).pipe(exhaustMap(() => this.fetch(true)))
    ).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(result => {
      this.result = result;
      this.loading = false;
      this.lastUpdate = new Date();
      // Se quitan los borradores de lecturas que ya no están en la lista
      const ids = new Set(result.content.map(r => r.id));
      for (const id of [...this.drafts.keys()]) {
        if (!ids.has(id)) this.drafts.delete(id);
      }
    });

    this.reload$.next(false);
  }

  private fetch(silent: boolean) {
    const status = this.tab === 'PENDING' ? 'PENDING' : this.resolvedStatus;
    return this.reviews.list(status, this.parkingId, this.page, 20, silent).pipe(catchError(err => {
      this.loading = false;
      if (!silent) this.errorMessage = apiError(err, 'No se pudo cargar la cola de revisión');
      return EMPTY;
    }));
  }

  // ========================= FILTROS =========================

  setTab(tab: Tab): void {
    if (this.tab === tab) return;
    this.tab = tab;
    this.reloadFromStart();
  }

  reloadFromStart(): void {
    this.page = 0;
    this.loading = true;
    this.errorMessage = '';
    this.reload$.next(false);
  }

  goTo(page: number): void {
    if (!this.result || page < 0 || page >= this.result.totalPages) return;
    this.page = page;
    this.reload$.next(false);
  }

  // ========================= TARJETA =========================

  draft(r: PlateReview): ReviewDraft {
    let d = this.drafts.get(r.id);
    if (!d) {
      d = {
        plate: r.suggestedPlate ?? (r.detectedPlate ?? '').toUpperCase(),
        documentTypeId: null, documentNumber: '', discarding: false, reason: '', saving: false, error: ''
      };
      this.drafts.set(r.id, d);
    }
    return d;
  }

  parkingOf(r: PlateReview): Parking | undefined {
    return this.parkings.find(p => p.id === r.parkingId);
  }

  /** El parqueo exige documento: en la entrada es obligatorio; en la salida, si entró con uno */
  documentRequired(r: PlateReview): boolean {
    return !!this.parkingOf(r)?.requireDriverDocument;
  }

  canResolve(r: PlateReview): boolean {
    return r.direction === 'ENTRY' ? this.canEntry : this.canExit;
  }

  plateOk(d: ReviewDraft): boolean {
    return isValidPlate(d.plate);
  }

  documentError(r: PlateReview, d: ReviewDraft): string | null {
    if (!this.documentRequired(r)) return null;
    const type = this.documentTypes.find(t => t.id === d.documentTypeId);
    if (!type) return r.direction === 'ENTRY' ? 'Elige el tipo de documento' : null;
    return documentNumberError(type, d.documentNumber);
  }

  canConfirm(r: PlateReview, d: ReviewDraft): boolean {
    return this.plateOk(d) && !this.documentError(r, d) && !d.saving;
  }

  onPlateInput(d: ReviewDraft): void {
    d.plate = d.plate.toUpperCase();
  }

  confirm(r: PlateReview): void {
    const d = this.draft(r);
    if (!this.canConfirm(r, d)) return;
    const withDoc = this.documentRequired(r) && !!d.documentTypeId && !!d.documentNumber.trim();
    d.saving = true;
    d.error = '';
    this.reviews.confirm(r.id, {
      plateNumber: normalizePlate(d.plate),
      documentTypeId: withDoc ? d.documentTypeId : null,
      documentNumber: withDoc ? normalizeDocumentNumber(d.documentNumber) : null
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: t => {
        d.saving = false;
        this.successMessage = `${r.direction === 'ENTRY' ? 'Entrada' : 'Salida'} registrada: ${t.vehicle.plateNumber}`;
        this.successLink = t.id;
        this.reload$.next(true);
      },
      error: err => {
        d.saving = false;
        // Sigue pendiente (vehículo ya dentro, falta pagar, no estás de turno...)
        d.error = apiError(err, 'No se pudo confirmar la lectura');
      }
    });
  }

  discard(r: PlateReview): void {
    const d = this.draft(r);
    if (!d.reason.trim() || d.saving) return;
    d.saving = true;
    d.error = '';
    this.reviews.discard(r.id, d.reason.trim()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        d.saving = false;
        this.successMessage = `Lectura descartada (${r.detectedPlate || 'sin placa'})`;
        this.successLink = null;
        this.reload$.next(true);
      },
      error: err => {
        d.saving = false;
        d.error = apiError(err, 'No se pudo descartar');
      }
    });
  }

  /** Motivos rápidos para descartar */
  readonly quickReasons = ['No es un vehículo', 'Foto ilegible', 'Lectura duplicada', 'Vehículo de servicio'];

  /** Por qué llegó a la cola (texto para el operador) */
  reasonOf(r: PlateReview): string {
    if (!r.suggestedPlate) return 'La placa leída no tiene formato válido';
    if (r.confidence < MIN_PLATE_CONFIDENCE) return `Lectura poco confiable (menos de ${confidencePercent(MIN_PLATE_CONFIDENCE)})`;
    if (this.documentRequired(r)) return 'El parqueo exige el documento del conductor';
    if (r.direction === 'EXIT') return 'No se encontró una estadía en curso con esa placa';
    return 'Requiere confirmación';
  }

  /** "hace 3 min" */
  ago(iso: string): string {
    const min = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 60000));
    if (min < 1) return 'hace un momento';
    if (min < 60) return `hace ${min} min`;
    const h = Math.floor(min / 60);
    return h < 24 ? `hace ${h} h ${min % 60} min` : `hace ${Math.floor(h / 24)} d`;
  }

  confidenceLevel(c: number): 'ok' | 'mid' | 'low' {
    return c >= MIN_PLATE_CONFIDENCE ? 'ok' : c >= 0.5 ? 'mid' : 'low';
  }

  photoFailed = new Set<number>();
}
