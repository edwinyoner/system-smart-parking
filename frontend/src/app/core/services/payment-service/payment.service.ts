import { Injectable } from '@angular/core';
import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';
import {
  CreatePaymentRequest,
  Payment,
  PaymentConcept,
  PaymentFilters,
  RefundPaymentRequest,
} from '../../models/payment-service/payment.model';
import { ParkingPagedResponse } from '../../models/pagination.model';
import { SKIP_LOADING } from '../../interceptors/loading.interceptor';

/**
 * Pagos de estadías y multas (payment-service).
 * Permisos: payments.process (cobrar), payments.view (ver), payments.cancel, payments.refund.
 */
@Injectable({
  providedIn: 'root',
})
export class PaymentService {

  private apiUrl = `${environment.apiUrl}/payment-service/v1/payments`;
  private simulatorUrl = `${environment.apiUrl}/payment-service/v1/gateway-simulator/charges`;

  constructor(private http: HttpClient) {}

  /** Efectivo: vuelve COMPLETED. QR: vuelve PENDING con el QR para mostrar */
  create(request: CreatePaymentRequest): Observable<Payment> {
    return this.http.post<Payment>(this.apiUrl, request);
  }

  getAll(page = 0, size = 10, filters: PaymentFilters = {}, silent = false): Observable<ParkingPagedResponse<Payment>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filters.status) params = params.set('status', filters.status);
    if (filters.concept) params = params.set('concept', filters.concept);
    if (filters.paymentTypeId) params = params.set('paymentTypeId', filters.paymentTypeId);
    if (filters.from) params = params.set('from', filters.from);
    if (filters.to) params = params.set('to', filters.to);
    if (filters.search?.trim()) params = params.set('search', filters.search.trim());
    return this.http.get<ParkingPagedResponse<Payment>>(this.apiUrl, {
      params,
      context: new HttpContext().set(SKIP_LOADING, silent),
    });
  }

  /** silent: para consultar cada pocos segundos si un QR ya se pagó, sin spinner global */
  getById(id: number, silent = false): Observable<Payment> {
    return this.http.get<Payment>(`${this.apiUrl}/${id}`, {
      context: new HttpContext().set(SKIP_LOADING, silent),
    });
  }

  /** Pagos de una estadía o una multa, el más reciente primero */
  getBySource(concept: PaymentConcept, sourceId: number, silent = false): Observable<Payment[]> {
    return this.http.get<Payment[]>(`${this.apiUrl}/by-source/${concept}/${sourceId}`, {
      context: new HttpContext().set(SKIP_LOADING, silent),
    });
  }

  /** Anula un QR pendiente */
  cancel(id: number, reason: string): Observable<Payment> {
    return this.http.post<Payment>(`${this.apiUrl}/${id}/cancel`, { reason });
  }

  /** Devolución manual (total o parcial) */
  refund(id: number, request: RefundPaymentRequest): Observable<Payment> {
    return this.http.post<Payment>(`${this.apiUrl}/${id}/refund`, request);
  }

  // ===== Pasarela simulada (solo tesis): hace de la app del banco que paga el QR =====

  simulatePay(gatewayReference: string): Observable<unknown> {
    return this.http.post(`${this.simulatorUrl}/${gatewayReference}/pay`, {});
  }

  simulateReject(gatewayReference: string): Observable<unknown> {
    return this.http.post(`${this.simulatorUrl}/${gatewayReference}/reject`, {});
  }
}
