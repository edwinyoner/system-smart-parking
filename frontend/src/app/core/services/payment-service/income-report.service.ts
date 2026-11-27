// src/app/core/services/payment-service/income-report.service.ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { IncomeReport } from '../../models/payment-service/income-report.model';
import { SKIP_LOADING } from '../../interceptors/loading.interceptor';

/** Reporte de ingresos de payment-service (permiso reports.view) */
@Injectable({
  providedIn: 'root'
})
export class IncomeReportService {

  private apiUrl = `${environment.apiUrl}/payment-service/v1/reports`;

  constructor(private http: HttpClient) {}

  /** from y to incluidos (máximo un año); parkingId null = todos */
  income(from: string, to: string, parkingId: number | null, silent = false): Observable<IncomeReport> {
    let params = new HttpParams().set('from', from).set('to', to);
    if (parkingId) params = params.set('parkingId', parkingId);
    return this.http.get<IncomeReport>(`${this.apiUrl}/income`,
      { params, context: new HttpContext().set(SKIP_LOADING, silent) });
  }
}
