// src/app/core/services/parking-service/space.service.ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { Space, SpaceRequest, SpaceUpdateRequest } from '../../models/parking-service/space.model';
import { ParkingPagedResponse } from '../../models/pagination.model';
import { SKIP_LOADING } from '../../interceptors/loading.interceptor';

/** Filtros de la lista de espacios */
export interface SpaceFilters {
  search?: string;
  status?: string;
  type?: string;
  zoneId?: number;
  parkingId?: number;
  deleted?: boolean;
}

/** Máximo de espacios por petición de lote (lo mismo que acepta el backend) */
export const SPACE_BATCH_MAX = 500;

@Injectable({
  providedIn: 'root',
})
export class SpaceService {
  private apiUrl = `${environment.apiUrl}/parking-service/v1/spaces`;

  constructor(private http: HttpClient) {}

  getAll(
    page: number = 0,
    size: number = 20,
    search?: string,
    status?: string,
  ): Observable<ParkingPagedResponse<Space>> {
    return this.search(page, size, { search, status });
  }

  search(page: number, size: number, filters: SpaceFilters, silent = false): Observable<ParkingPagedResponse<Space>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    if (filters.search && filters.search.trim()) params = params.set('search', filters.search.trim());
    if (filters.status) params = params.set('status', filters.status);
    if (filters.type) params = params.set('type', filters.type);
    if (filters.zoneId) params = params.set('zoneId', filters.zoneId.toString());
    if (filters.parkingId) params = params.set('parkingId', filters.parkingId.toString());
    if (filters.deleted) params = params.set('deleted', 'true');
    return this.http.get<ParkingPagedResponse<Space>>(this.apiUrl, {
      params,
      context: new HttpContext().set(SKIP_LOADING, silent),
    });
  }

  /** Todos los espacios vigentes de la zona */
  getByZoneId(zoneId: number, silent = false): Observable<Space[]> {
    return this.http.get<Space[]>(`${this.apiUrl}/zone/${zoneId}`, {
      context: new HttpContext().set(SKIP_LOADING, silent),
    });
  }

  /** Libres para registrar una entrada (vacío si la zona o el parqueo no están activos) */
  getAvailableByZoneId(zoneId: number): Observable<Space[]> {
    return this.http.get<Space[]>(`${this.apiUrl}/zone/${zoneId}/available`);
  }

  getById(id: number, silent = false): Observable<Space> {
    return this.http.get<Space>(`${this.apiUrl}/${id}`, {
      context: new HttpContext().set(SKIP_LOADING, silent),
    });
  }

  create(space: SpaceRequest): Observable<Space> {
    return this.http.post<Space>(this.apiUrl, space);
  }

  /** Todo o nada, máximo SPACE_BATCH_MAX por petición */
  createBatch(spaces: SpaceRequest[]): Observable<Space[]> {
    return this.http.post<Space[]>(`${this.apiUrl}/batch`, spaces);
  }

  update(id: number, space: SpaceUpdateRequest): Observable<Space> {
    return this.http.put<Space>(`${this.apiUrl}/${id}`, space);
  }

  /** Lógico: 409 con un vehículo dentro */
  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  /** Vuelve libre; exige su zona vigente */
  restore(id: number): Observable<Space> {
    return this.http.post<Space>(`${this.apiUrl}/${id}/restore`, {});
  }

  toggleStatus(id: number): Observable<Space> {
    return this.http.patch<Space>(`${this.apiUrl}/${id}/toggle-status`, {});
  }

  /** Libera a mano: 409 si hay una transacción activa (se libera registrando la salida) */
  release(id: number): Observable<Space> {
    return this.http.patch<Space>(`${this.apiUrl}/${id}/release`, {});
  }

  occupy(id: number): Observable<Space> {
    return this.http.patch<Space>(`${this.apiUrl}/${id}/occupy`, {});
  }

  setInMaintenance(id: number): Observable<Space> {
    return this.http.patch<Space>(`${this.apiUrl}/${id}/maintenance`, {});
  }

  setOutOfService(id: number): Observable<Space> {
    return this.http.patch<Space>(`${this.apiUrl}/${id}/out-of-service`, {});
  }
}
