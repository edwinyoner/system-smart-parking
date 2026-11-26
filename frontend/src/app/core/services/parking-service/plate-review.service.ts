// src/app/core/services/parking-service/plate-review.service.ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ConfirmPlateReviewRequest, PlateReview, PlateReviewStatus } from '../../models/parking-service/plate-review.model';
import { TransactionDetailResponse } from '../../models/parking-service/transaction.model';
import { ParkingPagedResponse } from '../../models/pagination.model';
import { SKIP_LOADING } from '../../interceptors/loading.interceptor';

/**
 * Cola "Por revisar". Ver: transactions.view. Confirmar o descartar: transactions.entry o
 * transactions.exit (el backend exige el de la dirección de la lectura) y estar de turno.
 */
@Injectable({
  providedIn: 'root'
})
export class PlateReviewService {

  private apiUrl = `${environment.apiUrl}/parking-service/v1/plate-reviews`;

  constructor(private http: HttpClient) {}

  /** status 'ALL' = todas; las pendientes llegan de la más antigua a la más nueva */
  list(status: PlateReviewStatus | 'ALL' = 'PENDING', parkingId: number | null = null, page = 0, size = 20,
       silent = false): Observable<ParkingPagedResponse<PlateReview>> {
    let params = new HttpParams().set('status', status).set('page', page).set('size', size);
    if (parkingId != null) {
      params = params.set('parkingId', parkingId);
    }
    return this.http.get<ParkingPagedResponse<PlateReview>>(this.apiUrl,
      { params, context: new HttpContext().set(SKIP_LOADING, silent) });
  }

  getById(id: number, silent = false): Observable<PlateReview> {
    return this.http.get<PlateReview>(`${this.apiUrl}/${id}`, { context: new HttpContext().set(SKIP_LOADING, silent) });
  }

  /** Registra la entrada o la salida; si falla (ya dentro, falta pagar...), sigue pendiente */
  confirm(id: number, request: ConfirmPlateReviewRequest): Observable<TransactionDetailResponse> {
    return this.http.post<TransactionDetailResponse>(`${this.apiUrl}/${id}/confirm`, request);
  }

  discard(id: number, reason: string): Observable<PlateReview> {
    return this.http.post<PlateReview>(`${this.apiUrl}/${id}/discard`, { reason });
  }
}
