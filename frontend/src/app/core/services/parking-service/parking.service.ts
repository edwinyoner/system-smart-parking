// src/app/core/services/parking-service/parking.service.ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { Parking, ParkingRequest, ParkingUpdateRequest } from '../../models/parking-service/parking.model';
import { ParkingPagedResponse } from '../../models/pagination.model';
import { SKIP_LOADING } from '../../interceptors/loading.interceptor';

/**
 * Parqueos (parking-service). Cada ruta exige su permiso parkings.*; /active solo sesión.
 * silent = true: actualización en segundo plano, sin spinner global.
 */
@Injectable({
  providedIn: 'root',
})
export class ParkingService {
  private apiUrl = `${environment.apiUrl}/parking-service/v1/parkings`;

  constructor(private http: HttpClient) {}

  getAll(
    page: number = 0,
    size: number = 20,
    search?: string,
    status?: string,
    deleted = false,
    silent = false,
  ): Observable<ParkingPagedResponse<Parking>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    if (search && search.trim()) {
      params = params.set('search', search.trim());
    }
    if (status) {
      params = params.set('status', status);
    }
    // Papelera: exige parkings.restore
    if (deleted) {
      params = params.set('deleted', 'true');
    }
    return this.http.get<ParkingPagedResponse<Parking>>(this.apiUrl, {
      params,
      context: new HttpContext().set(SKIP_LOADING, silent),
    });
  }

  /** Parqueos activos con su capacidad (selectores de entrada, transacciones, etc.) */
  getActive(): Observable<Parking[]> {
    return this.http.get<Parking[]>(`${this.apiUrl}/active`);
  }

  getById(id: number, silent = false): Observable<Parking> {
    return this.http.get<Parking>(`${this.apiUrl}/${id}`, {
      context: new HttpContext().set(SKIP_LOADING, silent),
    });
  }

  create(parking: ParkingRequest): Observable<Parking> {
    return this.http.post<Parking>(this.apiUrl, parking);
  }

  /** Reemplazo completo: un responsable omitido se quita */
  update(id: number, parking: ParkingUpdateRequest): Observable<Parking> {
    return this.http.put<Parking>(`${this.apiUrl}/${id}`, parking);
  }

  /** Lógico: 409 si tiene vehículos dentro */
  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  /** Vuelve activo con sus zonas, espacios, horario y operadores */
  restore(id: number): Observable<Parking> {
    return this.http.post<Parking>(`${this.apiUrl}/${id}/restore`, {});
  }

  toggleStatus(id: number): Observable<Parking> {
    return this.http.patch<Parking>(`${this.apiUrl}/${id}/toggle-status`, {});
  }

  activate(id: number): Observable<Parking> {
    return this.http.patch<Parking>(`${this.apiUrl}/${id}/activate`, {});
  }

  deactivate(id: number): Observable<Parking> {
    return this.http.patch<Parking>(`${this.apiUrl}/${id}/deactivate`, {});
  }

  setInMaintenance(id: number): Observable<Parking> {
    return this.http.patch<Parking>(`${this.apiUrl}/${id}/maintenance`, {});
  }

  setOutOfService(id: number): Observable<Parking> {
    return this.http.patch<Parking>(`${this.apiUrl}/${id}/out-of-service`, {});
  }
}
