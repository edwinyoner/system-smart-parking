// src/app/core/services/parking-service/parking-shift-rate.service.ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  ConfigureParkingShiftRatesRequest,
  ParkingScheduleStatus,
  ParkingShiftRate,
} from '../../models/parking-service/parking-shift-rate.model';
import { SKIP_LOADING } from '../../interceptors/loading.interceptor';

/**
 * Horario del parqueo: qué turnos atiende y con qué tarifa (vale para todas sus zonas).
 * Leer exige parkings.read; cambiar parkings.update.
 */
@Injectable({
  providedIn: 'root'
})
export class ParkingShiftRateService {
  private apiUrl = `${environment.apiUrl}/parking-service/v1/parkings`;

  constructor(private http: HttpClient) {}

  /** Reemplaza todo el horario (409 si dos turnos activos se solapan) */
  configure(parkingId: number, request: ConfigureParkingShiftRatesRequest): Observable<ParkingShiftRate[]> {
    return this.http.post<ParkingShiftRate[]>(`${this.apiUrl}/${parkingId}/shift-rates`, request);
  }

  /** Ordenado por hora de inicio */
  getByParkingId(parkingId: number, silent = false): Observable<ParkingShiftRate[]> {
    return this.http.get<ParkingShiftRate[]>(`${this.apiUrl}/${parkingId}/shift-rates`, {
      context: new HttpContext().set(SKIP_LOADING, silent),
    });
  }

  /** ¿Atiende ahora y con qué turno y tarifa? (solo sesión) */
  getCurrent(parkingId: number, silent = false): Observable<ParkingScheduleStatus> {
    return this.http.get<ParkingScheduleStatus>(`${this.apiUrl}/${parkingId}/shift-rates/current`, {
      context: new HttpContext().set(SKIP_LOADING, silent),
    });
  }

  changeRate(configId: number, rateId: number): Observable<ParkingShiftRate> {
    return this.http.patch<ParkingShiftRate>(`${this.apiUrl}/shift-rates/${configId}/rate`, { rateId });
  }

  /** 409 si el turno tiene operadores asignados */
  delete(configId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/shift-rates/${configId}`);
  }

  /** Al activar, 409 si se solapa con otro turno activo */
  toggleStatus(configId: number): Observable<ParkingShiftRate> {
    return this.http.patch<ParkingShiftRate>(`${this.apiUrl}/shift-rates/${configId}/toggle`, {});
  }
}
