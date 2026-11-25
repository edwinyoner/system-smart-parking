//src/app/core/services/parking-service/rate.service.ts
import { Injectable } from "@angular/core";
import { HttpClient, HttpContext, HttpParams } from "@angular/common/http";
import { Observable } from "rxjs";
import { environment } from "../../../../environments/environment";
import { Rate, RateRequest, RateSimulation } from "../../models/parking-service/rate.model";
import { SKIP_LOADING } from "../../interceptors/loading.interceptor";
// ParkingPagedResponse para parking-service
import { ParkingPagedResponse } from "../../models/pagination.model";

@Injectable({
  providedIn: "root",
})
export class RateService {
  private apiUrl = `${environment.apiUrl}/parking-service/v1/rates`;

  constructor(private http: HttpClient) {}

  getAll(
    page: number = 0,
    size: number = 20,
    search?: string,
    status?: string, // "true" | "false" | undefined
    deleted = false,  // papelera (exige rates.restore)
    silent = false,   // actualización en segundo plano, sin spinner global
  ): Observable<ParkingPagedResponse<Rate>> {
    let params = new HttpParams()
      .set("page", page.toString())
      .set("size", size.toString());

    if (search && search.trim()) {
      params = params.set("search", search.trim());
    }
    if (status !== undefined && status !== "") {
      params = params.set("status", status);
    }
    if (deleted) {
      params = params.set("deleted", "true");
    }

    return this.http.get<ParkingPagedResponse<Rate>>(this.apiUrl, {
      params,
      context: new HttpContext().set(SKIP_LOADING, silent),
    });
  }

  getActive(): Observable<Rate[]> {
    return this.http.get<Rate[]>(`${this.apiUrl}/active`);
  }

  getById(id: number, silent = false): Observable<Rate> {
    return this.http.get<Rate>(`${this.apiUrl}/${id}`, {
      context: new HttpContext().set(SKIP_LOADING, silent),
    });
  }

  /** Cuánto se cobraría con estos datos (no guarda nada). Mismo cálculo que la salida */
  simulate(data: Omit<RateRequest, 'name' | 'description' | 'status'>, durations?: number[]): Observable<RateSimulation> {
    return this.http.post<RateSimulation>(`${this.apiUrl}/simulate`, { ...data, durations }, {
      context: new HttpContext().set(SKIP_LOADING, true),
    });
  }

  /** Deshace la eliminación: la tarifa vuelve activa */
  restore(id: number): Observable<Rate> {
    return this.http.post<Rate>(`${this.apiUrl}/${id}/restore`, {});
  }

  create(rate: RateRequest): Observable<Rate> {
    return this.http.post<Rate>(this.apiUrl, rate);
  }

  update(id: number, rate: RateRequest): Observable<Rate> {
    return this.http.put<Rate>(`${this.apiUrl}/${id}`, rate);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  toggleActive(id: number): Observable<Rate> {
    return this.http.patch<Rate>(`${this.apiUrl}/${id}/toggle-status`, {});
  }
}
