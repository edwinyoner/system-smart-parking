import { Injectable } from '@angular/core';
import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { PaymentChannel, PaymentType, PaymentTypeRequest } from '../../models/payment-service/payment-type.model';
import { ParkingPagedResponse } from '../../models/pagination.model';
import { SKIP_LOADING } from '../../interceptors/loading.interceptor';

/** Tipos de pago: viven en payment-service (antes en parking-service) */
@Injectable({
  providedIn: 'root',
})
export class PaymentTypeService {

  private apiUrl = `${environment.apiUrl}/payment-service/v1/payment-types`;

  constructor(private http: HttpClient) {}

  getAll(
    page: number = 0,
    size: number = 20,
    search?: string,
    status?: string,
    deleted = false,  // papelera (exige payment-types.restore)
    silent = false,   // actualización en segundo plano, sin spinner global
  ): Observable<ParkingPagedResponse<PaymentType>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    if (search && search.trim()) {
      params = params.set('search', search.trim());
    }
    if (status !== undefined && status !== '') {
      params = params.set('status', status);
    }
    if (deleted) {
      params = params.set('deleted', 'true');
    }
    return this.http.get<ParkingPagedResponse<PaymentType>>(this.apiUrl, {
      params,
      context: new HttpContext().set(SKIP_LOADING, silent),
    });
  }

  /** Activos; con channel, solo los que se ofrecen en ese canal (por ejemplo, OPERATOR) */
  getActive(channel?: PaymentChannel): Observable<PaymentType[]> {
    const params = channel ? new HttpParams().set('channel', channel) : undefined;
    return this.http.get<PaymentType[]>(`${this.apiUrl}/active`, { params });
  }

  getById(id: number, silent = false): Observable<PaymentType> {
    return this.http.get<PaymentType>(`${this.apiUrl}/${id}`, {
      context: new HttpContext().set(SKIP_LOADING, silent),
    });
  }

  create(paymentType: PaymentTypeRequest): Observable<PaymentType> {
    return this.http.post<PaymentType>(this.apiUrl, paymentType);
  }

  update(id: number, paymentType: PaymentTypeRequest): Observable<PaymentType> {
    return this.http.put<PaymentType>(`${this.apiUrl}/${id}`, paymentType);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  /** Deshace la eliminación: el tipo vuelve activo */
  restore(id: number): Observable<PaymentType> {
    return this.http.post<PaymentType>(`${this.apiUrl}/${id}/restore`, {});
  }

  toggleActive(id: number): Observable<PaymentType> {
    return this.http.patch<PaymentType>(`${this.apiUrl}/${id}/toggle-status`, {});
  }

  activate(id: number): Observable<PaymentType> {
    return this.http.patch<PaymentType>(`${this.apiUrl}/${id}/activate`, {});
  }

  deactivate(id: number): Observable<PaymentType> {
    return this.http.patch<PaymentType>(`${this.apiUrl}/${id}/deactivate`, {});
  }
}
