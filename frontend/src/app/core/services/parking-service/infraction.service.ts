// src/app/core/services/parking-service/infraction.service.ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  EditInfractionRequest,
  Infraction,
  InfractionFilters,
  InfractionSummary,
  RegisterInfractionRequest
} from '../../models/parking-service/infraction.model';
import { ParkingPagedResponse } from '../../models/pagination.model';
import { SKIP_LOADING } from '../../interceptors/loading.interceptor';

/**
 * Infracciones. Permisos: infractions.read (consultas), infractions.create (registrar, estando de turno),
 * infractions.update (editar y cambiar la multa), infractions.resolve (anular o amonestar).
 * El cobro de la multa es de payment-service (concepto FINE, componente app-charge-payment).
 * `silent = true` evita el indicador de carga global (refresco en vivo).
 */
@Injectable({
  providedIn: 'root'
})
export class InfractionService {

  private apiUrl = `${environment.apiUrl}/parking-service/v1/infractions`;

  constructor(private http: HttpClient) {}

  private ctx(silent: boolean): HttpContext {
    return new HttpContext().set(SKIP_LOADING, silent);
  }

  // ========================= CONSULTAS =========================

  list(page: number, size: number, filters: InfractionFilters = {}, silent = false):
    Observable<ParkingPagedResponse<Infraction>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filters.status && filters.status !== 'ALL') params = params.set('status', filters.status);
    if (filters.parkingId) params = params.set('parkingId', filters.parkingId);
    if (filters.type) params = params.set('type', filters.type);
    if (filters.search?.trim()) params = params.set('search', filters.search.trim());
    if (filters.from) params = params.set('from', filters.from);
    if (filters.to) params = params.set('to', filters.to);
    return this.http.get<ParkingPagedResponse<Infraction>>(this.apiUrl, { params, context: this.ctx(silent) });
  }

  summary(parkingId: number | null = null, silent = false): Observable<InfractionSummary> {
    let params = new HttpParams();
    if (parkingId) params = params.set('parkingId', parkingId);
    return this.http.get<InfractionSummary>(`${this.apiUrl}/summary`, { params, context: this.ctx(silent) });
  }

  getById(id: number, silent = false): Observable<Infraction> {
    return this.http.get<Infraction>(`${this.apiUrl}/${id}`, { context: this.ctx(silent) });
  }

  /** Infracciones de una estadía (detalle de la transacción) */
  getByTransaction(transactionId: number, silent = false): Observable<Infraction[]> {
    return this.http.get<Infraction[]>(`${this.apiUrl}/transaction/${transactionId}`, { context: this.ctx(silent) });
  }

  // ========================= CAMBIOS =========================

  /** 403 si no está de turno en ese parqueo o zona */
  register(request: RegisterInfractionRequest): Observable<Infraction> {
    return this.http.post<Infraction>(this.apiUrl, request);
  }

  /** Solo pendiente y sin pagos (409 si no) */
  edit(id: number, request: EditInfractionRequest): Observable<Infraction> {
    return this.http.put<Infraction>(`${this.apiUrl}/${id}`, request);
  }

  /** fineAmount null quita la multa */
  changeFine(id: number, fineAmount: number | null, dueDate: string | null): Observable<Infraction> {
    return this.http.patch<Infraction>(`${this.apiUrl}/${id}/fine`, { fineAmount, dueDate });
  }

  /** Anular con motivo (409 si tiene pagos) */
  dismiss(id: number, reason: string): Observable<Infraction> {
    return this.http.post<Infraction>(`${this.apiUrl}/${id}/dismiss`, { reason });
  }

  /** Cerrar con amonestación (solo sin multa) */
  closeWithWarning(id: number, comment: string | null): Observable<Infraction> {
    return this.http.post<Infraction>(`${this.apiUrl}/${id}/warning`, { reason: comment });
  }
}
