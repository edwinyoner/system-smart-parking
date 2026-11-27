// src/app/core/services/parking-service/customer.service.ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  Customer,
  CustomerDetail,
  StayHistory,
  UpdateCustomerContactRequest
} from '../../models/parking-service/customer.model';
import { Infraction } from '../../models/parking-service/infraction.model';
import { ParkingPagedResponse } from '../../models/pagination.model';
import { SKIP_LOADING } from '../../interceptors/loading.interceptor';

/**
 * Clientes (consulta e historial). Se crean solos al entrar con documento; aquí solo se
 * editan sus datos de contacto. Permisos: customers.read, customers.update.
 */
@Injectable({
  providedIn: 'root'
})
export class CustomerService {

  private apiUrl = `${environment.apiUrl}/parking-service/v1/customers`;

  constructor(private http: HttpClient) {}

  private ctx(silent: boolean): HttpContext {
    return new HttpContext().set(SKIP_LOADING, silent);
  }

  private page(page: number, size: number): HttpParams {
    return new HttpParams().set('page', page).set('size', size);
  }

  /** search: parte del nombre, documento, celular o correo */
  list(page: number, size: number, search = '', silent = false): Observable<ParkingPagedResponse<Customer>> {
    let params = this.page(page, size);
    if (search.trim()) params = params.set('search', search.trim());
    return this.http.get<ParkingPagedResponse<Customer>>(this.apiUrl, { params, context: this.ctx(silent) });
  }

  getById(id: number, silent = false): Observable<CustomerDetail> {
    return this.http.get<CustomerDetail>(`${this.apiUrl}/${id}`, { context: this.ctx(silent) });
  }

  getStays(id: number, page: number, size: number, silent = false): Observable<ParkingPagedResponse<StayHistory>> {
    return this.http.get<ParkingPagedResponse<StayHistory>>(`${this.apiUrl}/${id}/stays`,
      { params: this.page(page, size), context: this.ctx(silent) });
  }

  getInfractions(id: number, page: number, size: number, silent = false): Observable<ParkingPagedResponse<Infraction>> {
    return this.http.get<ParkingPagedResponse<Infraction>>(`${this.apiUrl}/${id}/infractions`,
      { params: this.page(page, size), context: this.ctx(silent) });
  }

  updateContact(id: number, request: UpdateCustomerContactRequest): Observable<Customer> {
    return this.http.patch<Customer>(`${this.apiUrl}/${id}/contact`, request);
  }
}
