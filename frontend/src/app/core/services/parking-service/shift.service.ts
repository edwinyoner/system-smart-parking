//src/app/core/services/parking-service/shift.service.ts
import { Injectable } from "@angular/core";
import { HttpClient, HttpContext, HttpParams } from "@angular/common/http";
import { Observable } from "rxjs";
import { environment } from "../../../../environments/environment";
import { Shift, ShiftRequest } from "../../models/parking-service/shift.model";
// Importar ParkingPagedResponse en lugar de PaginatedResponse
import { ParkingPagedResponse } from "../../models/pagination.model";
import { SKIP_LOADING } from "../../interceptors/loading.interceptor";

@Injectable({
  providedIn: "root",
})
export class ShiftService {
  private apiUrl = `${environment.apiUrl}/parking-service/v1/shifts`;

  constructor(private http: HttpClient) {}

  getAll(
    page: number = 0,
    size: number = 20,
    search?: string,
    status?: string,
    deleted = false,
    silent = false,   // true: actualización en segundo plano, sin spinner global
  ): Observable<ParkingPagedResponse<Shift>> {
    let params = new HttpParams()
      .set("page", page.toString())
      .set("size", size.toString());

    if (search && search.trim()) {
      params = params.set("search", search.trim());
    }
    if (status !== undefined && status !== "") {
      params = params.set("status", status);
    }
    // Papelera: turnos eliminados (exige shifts.delete)
    if (deleted) {
      params = params.set("deleted", "true");
    }
      
    return this.http.get<ParkingPagedResponse<Shift>>(this.apiUrl, {
      params,
      context: new HttpContext().set(SKIP_LOADING, silent),
    });
  }

  getActive(): Observable<Shift[]> {
    return this.http.get<Shift[]>(`${this.apiUrl}/active`);
  }

  getById(id: number, silent = false): Observable<Shift> {
    return this.http.get<Shift>(`${this.apiUrl}/${id}`, {
      context: new HttpContext().set(SKIP_LOADING, silent),
    });
  }

  create(shift: ShiftRequest): Observable<Shift> {
    return this.http.post<Shift>(this.apiUrl, shift);
  }

  update(id: number, shift: ShiftRequest): Observable<Shift> {
    return this.http.put<Shift>(`${this.apiUrl}/${id}`, shift);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  /** Deshace la eliminación: el turno vuelve activo con su mismo código */
  restore(id: number): Observable<Shift> {
    return this.http.post<Shift>(`${this.apiUrl}/${id}/restore`, {});
  }

  toggleActive(id: number): Observable<Shift> {
    return this.http.patch<Shift>(`${this.apiUrl}/${id}/toggle-status`, {});
  }
}
