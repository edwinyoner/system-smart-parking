// src/app/core/services/parking-service/vehicle.service.ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { Vehicle, VehicleDetail } from '../../models/parking-service/vehicle.model';
import { StayHistory } from '../../models/parking-service/customer.model';
import { Infraction } from '../../models/parking-service/infraction.model';
import { ParkingPagedResponse } from '../../models/pagination.model';
import { SKIP_LOADING } from '../../interceptors/loading.interceptor';

/** Vehículos (solo consulta; se crean solos por placa). Permiso: vehicles.read */
@Injectable({
  providedIn: 'root'
})
export class VehicleService {

  private apiUrl = `${environment.apiUrl}/parking-service/v1/vehicles`;

  constructor(private http: HttpClient) {}

  private ctx(silent: boolean): HttpContext {
    return new HttpContext().set(SKIP_LOADING, silent);
  }

  private page(page: number, size: number): HttpParams {
    return new HttpParams().set('page', page).set('size', size);
  }

  /** search: parte de la placa, con o sin guion */
  list(page: number, size: number, search = '', silent = false): Observable<ParkingPagedResponse<Vehicle>> {
    let params = this.page(page, size);
    if (search.trim()) params = params.set('search', search.trim());
    return this.http.get<ParkingPagedResponse<Vehicle>>(this.apiUrl, { params, context: this.ctx(silent) });
  }

  getById(id: number, silent = false): Observable<VehicleDetail> {
    return this.http.get<VehicleDetail>(`${this.apiUrl}/${id}`, { context: this.ctx(silent) });
  }

  getStays(id: number, page: number, size: number, silent = false): Observable<ParkingPagedResponse<StayHistory>> {
    return this.http.get<ParkingPagedResponse<StayHistory>>(`${this.apiUrl}/${id}/stays`,
      { params: this.page(page, size), context: this.ctx(silent) });
  }

  getInfractions(id: number, page: number, size: number, silent = false): Observable<ParkingPagedResponse<Infraction>> {
    return this.http.get<ParkingPagedResponse<Infraction>>(`${this.apiUrl}/${id}/infractions`,
      { params: this.page(page, size), context: this.ctx(silent) });
  }
}
