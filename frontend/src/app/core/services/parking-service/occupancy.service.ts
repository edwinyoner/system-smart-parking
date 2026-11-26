// src/app/core/services/parking-service/occupancy.service.ts
import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { SKIP_LOADING } from '../../interceptors/loading.interceptor';
import { OccupancyParkingMap, OccupancyParkingSummary } from '../../models/parking-service/occupancy.model';

/** Mapa de ocupación (exige occupancy.view). silent = sin spinner global, para el refresco en vivo */
@Injectable({ providedIn: 'root' })
export class OccupancyService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/parking-service/v1/occupancy`;

  listParkings(silent = false): Observable<OccupancyParkingSummary[]> {
    return this.http.get<OccupancyParkingSummary[]>(`${this.apiUrl}/parkings`, {
      context: new HttpContext().set(SKIP_LOADING, silent),
    });
  }

  getParkingMap(parkingId: number, silent = false): Observable<OccupancyParkingMap> {
    return this.http.get<OccupancyParkingMap>(`${this.apiUrl}/parkings/${parkingId}`, {
      context: new HttpContext().set(SKIP_LOADING, silent),
    });
  }
}
