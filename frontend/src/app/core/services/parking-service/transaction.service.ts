// src/app/core/services/parking-service/transaction.service.ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  TransactionDetailResponse,
  ActiveTransactionDto,
  TransactionDto,
  TransactionEntryRequest,
  TransactionExitRequest
} from '../../models/parking-service/transaction.model';
import { ParkingPagedResponse } from '../../models/pagination.model';
import { SKIP_LOADING } from '../../interceptors/loading.interceptor';

/** Campos por los que el backend permite ordenar el historial */
export type TransactionSortField = 'entryTime' | 'exitTime' | 'createdAt' | 'totalAmount';

/**
 * Estadías. Permisos: transactions.entry (entrar, cambiar espacio), transactions.exit (salir),
 * transactions.view (consultas), transactions.cancel (cancelar). Entrar y salir exige además
 * estar de turno en ese parqueo (403 si no).
 * `silent = true` evita el indicador de carga global (refresco en vivo).
 */
export interface ActiveTransactionFilters {
  parkingId?: number | null;
  zoneId?: number | null;
  plate?: string | null;
}

@Injectable({
  providedIn: 'root'
})
export class TransactionService {

  private apiUrl = `${environment.apiUrl}/parking-service/v1/transactions`;

  constructor(private http: HttpClient) {}

  private ctx(silent: boolean): HttpContext {
    return new HttpContext().set(SKIP_LOADING, silent);
  }

  private page(page: number, size: number): HttpParams {
    return new HttpParams().set('page', page).set('size', size);
  }

  // ========================= ENTRADA, SALIDA Y CAMBIOS =========================

  recordEntry(request: TransactionEntryRequest): Observable<TransactionDetailResponse> {
    return this.http.post<TransactionDetailResponse>(`${this.apiUrl}/entry`, request);
  }

  /** 409 si falta pagar: se cobra y se vuelve a intentar */
  recordExit(request: TransactionExitRequest): Observable<TransactionDetailResponse> {
    return this.http.post<TransactionDetailResponse>(`${this.apiUrl}/exit`, request);
  }

  /** El vehículo estacionó en otro espacio libre de la misma zona */
  reassignSpace(id: number, spaceId: number): Observable<TransactionDetailResponse> {
    return this.http.patch<TransactionDetailResponse>(`${this.apiUrl}/${id}/space`, { spaceId });
  }

  updateNotes(id: number, notes: string | null): Observable<TransactionDto> {
    return this.http.patch<TransactionDto>(`${this.apiUrl}/${id}/notes`, { notes });
  }

  /** Solo en curso y sin pagos; libera el espacio */
  cancel(id: number, reason: string): Observable<TransactionDto> {
    return this.http.post<TransactionDto>(`${this.apiUrl}/${id}/cancel`, { reason });
  }

  // ========================= CONSULTAS =========================

  getById(id: number, silent = false): Observable<TransactionDetailResponse> {
    return this.http.get<TransactionDetailResponse>(`${this.apiUrl}/${id}`, { context: this.ctx(silent) });
  }

  /** 404 si la placa no tiene una estadía en curso */
  getActiveByPlate(plateNumber: string, silent = false): Observable<TransactionDetailResponse> {
    return this.http.get<TransactionDetailResponse>(
      `${this.apiUrl}/active/plate/${encodeURIComponent(plateNumber)}`, { context: this.ctx(silent) });
  }

  // ========================= EN CURSO =========================

  /** Vehículos dentro, los más recientes primero. Filtros opcionales: parqueo, zona y parte de la placa */
  getActiveTransactions(page = 0, size = 20, silent = false,
                        filters: ActiveTransactionFilters = {}): Observable<ParkingPagedResponse<ActiveTransactionDto>> {
    let params = this.page(page, size);
    if (filters.parkingId) params = params.set('parkingId', filters.parkingId);
    if (filters.zoneId) params = params.set('zoneId', filters.zoneId);
    if (filters.plate?.trim()) params = params.set('plate', filters.plate.trim());
    return this.http.get<ParkingPagedResponse<ActiveTransactionDto>>(`${this.apiUrl}/active`,
      { params, context: this.ctx(silent) });
  }

  getActiveByZone(zoneId: number, page = 0, size = 20, silent = false): Observable<ParkingPagedResponse<ActiveTransactionDto>> {
    return this.http.get<ParkingPagedResponse<ActiveTransactionDto>>(`${this.apiUrl}/active/zone/${zoneId}`,
      { params: this.page(page, size), context: this.ctx(silent) });
  }

  /** Búsqueda parcial: "abc1" encuentra ABC-123 */
  searchActiveByPlate(plateNumber: string, page = 0, size = 20, silent = false): Observable<ParkingPagedResponse<ActiveTransactionDto>> {
    return this.http.get<ParkingPagedResponse<ActiveTransactionDto>>(`${this.apiUrl}/active/search`,
      { params: this.page(page, size).set('plateNumber', plateNumber), context: this.ctx(silent) });
  }

  getOverdueTransactions(page = 0, size = 20, silent = false): Observable<ParkingPagedResponse<ActiveTransactionDto>> {
    return this.http.get<ParkingPagedResponse<ActiveTransactionDto>>(`${this.apiUrl}/active/overdue`,
      { params: this.page(page, size), context: this.ctx(silent) });
  }

  // ========================= HISTORIAL =========================

  getAll(page = 0, size = 20, sortBy: TransactionSortField = 'createdAt', sortDirection: 'ASC' | 'DESC' = 'DESC',
         silent = false): Observable<ParkingPagedResponse<TransactionDto>> {
    return this.http.get<ParkingPagedResponse<TransactionDto>>(this.apiUrl, {
      params: this.page(page, size).set('sortBy', sortBy).set('sortDirection', sortDirection),
      context: this.ctx(silent)
    });
  }

  /** Fechas ISO local, ej. 2026-11-26T00:00:00 */
  getByDateRange(startDate: string, endDate: string, page = 0, size = 20, silent = false): Observable<ParkingPagedResponse<TransactionDto>> {
    return this.http.get<ParkingPagedResponse<TransactionDto>>(`${this.apiUrl}/date-range`, {
      params: this.page(page, size).set('startDate', startDate).set('endDate', endDate),
      context: this.ctx(silent)
    });
  }

  getByStatus(status: string, page = 0, size = 20, silent = false): Observable<ParkingPagedResponse<TransactionDto>> {
    return this.http.get<ParkingPagedResponse<TransactionDto>>(`${this.apiUrl}/status/${status}`,
      { params: this.page(page, size), context: this.ctx(silent) });
  }

  getByPaymentStatus(paymentStatus: string, page = 0, size = 20, silent = false): Observable<ParkingPagedResponse<TransactionDto>> {
    return this.http.get<ParkingPagedResponse<TransactionDto>>(`${this.apiUrl}/payment-status/${paymentStatus}`,
      { params: this.page(page, size), context: this.ctx(silent) });
  }

  getByZone(zoneId: number, page = 0, size = 20, silent = false): Observable<ParkingPagedResponse<TransactionDto>> {
    return this.http.get<ParkingPagedResponse<TransactionDto>>(`${this.apiUrl}/zone/${zoneId}`,
      { params: this.page(page, size), context: this.ctx(silent) });
  }

  searchByPlate(plateNumber: string, page = 0, size = 20, silent = false): Observable<ParkingPagedResponse<TransactionDto>> {
    return this.http.get<ParkingPagedResponse<TransactionDto>>(`${this.apiUrl}/search`,
      { params: this.page(page, size).set('plateNumber', plateNumber), context: this.ctx(silent) });
  }
}
