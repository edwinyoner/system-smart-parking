// src/app/core/services/parking-service/zone.service.ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { Zone, ZoneRequest, ZoneUpdateRequest } from '../../models/parking-service/zone.model';
import { ParkingPagedResponse } from '../../models/pagination.model';
import { SKIP_LOADING } from '../../interceptors/loading.interceptor';

/**
 * Zonas de los parqueos. Los turnos y tarifas son del parqueo, no de la zona
 * (ver ParkingShiftRateService).
 */
@Injectable({
  providedIn: 'root',
})
export class ZoneService {
  private apiUrl = `${environment.apiUrl}/parking-service/v1/zones`;

  constructor(private http: HttpClient) {}

  getAll(
    page: number = 0,
    size: number = 20,
    search?: string,
    status?: string,
    parkingId?: number,
    deleted = false,
    silent = false,
  ): Observable<ParkingPagedResponse<Zone>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    if (search && search.trim()) {
      params = params.set('search', search.trim());
    }
    if (status) {
      params = params.set('status', status);
    }
    if (parkingId) {
      params = params.set('parkingId', parkingId.toString());
    }
    if (deleted) {
      params = params.set('deleted', 'true');
    }
    return this.http.get<ParkingPagedResponse<Zone>>(this.apiUrl, {
      params,
      context: new HttpContext().set(SKIP_LOADING, silent),
    });
  }

  /** Zonas activas, opcionalmente de un parqueo (solo exige sesión) */
  getActive(parkingId?: number): Observable<Zone[]> {
    let params = new HttpParams();
    if (parkingId) {
      params = params.set('parkingId', parkingId.toString());
    }
    return this.http.get<Zone[]>(`${this.apiUrl}/active`, { params });
  }

  /** Todas las zonas vigentes del parqueo, con su capacidad */
  getByParkingId(parkingId: number, silent = false): Observable<Zone[]> {
    return this.http.get<Zone[]>(`${this.apiUrl}/parking/${parkingId}`, {
      context: new HttpContext().set(SKIP_LOADING, silent),
    });
  }

  getById(id: number, silent = false): Observable<Zone> {
    return this.http.get<Zone>(`${this.apiUrl}/${id}`, {
      context: new HttpContext().set(SKIP_LOADING, silent),
    });
  }

  create(zone: ZoneRequest): Observable<Zone> {
    return this.http.post<Zone>(this.apiUrl, zone);
  }

  update(id: number, zone: ZoneUpdateRequest): Observable<Zone> {
    return this.http.put<Zone>(`${this.apiUrl}/${id}`, zone);
  }

  /** Lógico: 409 si tiene vehículos dentro */
  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  /** Exige que su parqueo siga vigente */
  restore(id: number): Observable<Zone> {
    return this.http.post<Zone>(`${this.apiUrl}/${id}/restore`, {});
  }

  toggleStatus(id: number): Observable<Zone> {
    return this.http.patch<Zone>(`${this.apiUrl}/${id}/toggle-status`, {});
  }

  activate(id: number): Observable<Zone> {
    return this.http.patch<Zone>(`${this.apiUrl}/${id}/activate`, {});
  }

  deactivate(id: number): Observable<Zone> {
    return this.http.patch<Zone>(`${this.apiUrl}/${id}/deactivate`, {});
  }

  setInMaintenance(id: number): Observable<Zone> {
    return this.http.patch<Zone>(`${this.apiUrl}/${id}/maintenance`, {});
  }

  setOutOfService(id: number): Observable<Zone> {
    return this.http.patch<Zone>(`${this.apiUrl}/${id}/out-of-service`, {});
  }
}
