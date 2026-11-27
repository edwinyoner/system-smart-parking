// src/app/core/services/parking-service/report.service.ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { DailyReport, OccupancyReport } from '../../models/parking-service/report.model';
import { SKIP_LOADING } from '../../interceptors/loading.interceptor';

/** Reportes de operación de parking-service (permiso reports.view) */
@Injectable({
  providedIn: 'root'
})
export class ReportService {

  private apiUrl = `${environment.apiUrl}/parking-service/v1/reports`;

  constructor(private http: HttpClient) {}

  /** parkingId null = todos los parqueos */
  daily(date: string, parkingId: number | null, silent = false): Observable<DailyReport> {
    let params = new HttpParams().set('date', date);
    if (parkingId) params = params.set('parkingId', parkingId);
    return this.http.get<DailyReport>(`${this.apiUrl}/daily`,
      { params, context: new HttpContext().set(SKIP_LOADING, silent) });
  }

  /** zoneId null = todo el parqueo */
  occupancy(date: string, parkingId: number, zoneId: number | null, silent = false): Observable<OccupancyReport> {
    let params = new HttpParams().set('date', date).set('parkingId', parkingId);
    if (zoneId) params = params.set('zoneId', zoneId);
    return this.http.get<OccupancyReport>(`${this.apiUrl}/occupancy`,
      { params, context: new HttpContext().set(SKIP_LOADING, silent) });
  }
}
