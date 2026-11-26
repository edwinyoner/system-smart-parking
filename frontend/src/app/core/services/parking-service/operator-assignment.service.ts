// src/app/core/services/parking-service/operator-assignment.service.ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  AssignOperatorRequest,
  AssignmentStatus,
  OperatorAssignment,
  UpdateOperatorAssignmentRequest,
} from '../../models/parking-service/operator-assignment.model';
import { SKIP_LOADING } from '../../interceptors/loading.interceptor';

/** Filtros: se exige el parqueo o el operador */
export interface AssignmentFilters {
  parkingId?: number;
  zoneId?: number;
  operatorId?: number;
  status?: AssignmentStatus | '';
}

/**
 * Asignación de operadores a parqueos y zonas por turno (permisos operator-assignments.*).
 */
@Injectable({
  providedIn: 'root',
})
export class OperatorAssignmentService {
  private apiUrl = `${environment.apiUrl}/parking-service/v1/operator-assignments`;

  constructor(private http: HttpClient) {}

  /** 409 si en ese turno ya hay operador de parqueo/zona o si el operador se cruza de horario */
  assign(request: AssignOperatorRequest): Observable<OperatorAssignment> {
    return this.http.post<OperatorAssignment>(this.apiUrl, request);
  }

  list(filters: AssignmentFilters, silent = false): Observable<OperatorAssignment[]> {
    let params = new HttpParams();
    if (filters.parkingId) params = params.set('parkingId', filters.parkingId.toString());
    if (filters.zoneId) params = params.set('zoneId', filters.zoneId.toString());
    if (filters.operatorId) params = params.set('operatorId', filters.operatorId.toString());
    if (filters.status) params = params.set('status', filters.status);
    return this.http.get<OperatorAssignment[]>(this.apiUrl, {
      params,
      context: new HttpContext().set(SKIP_LOADING, silent),
    });
  }

  /** Asignaciones de quien consulta */
  listMine(status?: AssignmentStatus, silent = false): Observable<OperatorAssignment[]> {
    let params = new HttpParams();
    if (status) params = params.set('status', status);
    return this.http.get<OperatorAssignment[]>(`${this.apiUrl}/me`, {
      params,
      context: new HttpContext().set(SKIP_LOADING, silent),
    });
  }

  getById(id: number): Observable<OperatorAssignment> {
    return this.http.get<OperatorAssignment>(`${this.apiUrl}/${id}`);
  }

  update(id: number, request: UpdateOperatorAssignmentRequest): Observable<OperatorAssignment> {
    return this.http.patch<OperatorAssignment>(`${this.apiUrl}/${id}`, request);
  }

  /**
   * Termina desde hoy. Si aún no había empezado se cancela: responde sin cuerpo (null).
   */
  finish(id: number): Observable<OperatorAssignment | null> {
    return this.http.post<OperatorAssignment | null>(`${this.apiUrl}/${id}/finish`, {});
  }
}
