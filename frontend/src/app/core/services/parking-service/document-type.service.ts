// src/app/core/services/parking-service/document-type.service.ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { DocumentType, DocumentTypeRequest } from '../../models/parking-service/document-type.model';
import { ParkingPagedResponse } from '../../models/pagination.model';
import { SKIP_LOADING } from '../../interceptors/loading.interceptor';

@Injectable({
  providedIn: 'root',
})
export class DocumentTypeService {
  private apiUrl = `${environment.apiUrl}/parking-service/v1/document-types`;

  constructor(private http: HttpClient) {}

  getAll(
    page: number = 0,
    size: number = 20,
    search?: string,
    status?: string,
    deleted = false,  // papelera (exige document-types.restore)
    silent = false,   // actualización en segundo plano, sin spinner global
  ): Observable<ParkingPagedResponse<DocumentType>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (search && search.trim()) {
      params = params.set('search', search.trim());
    }
    if (status !== undefined && status !== '') {
      params = params.set('status', status);
    }
    if (deleted) {
      params = params.set('deleted', 'true');
    }

    return this.http.get<ParkingPagedResponse<DocumentType>>(this.apiUrl, {
      params,
      context: new HttpContext().set(SKIP_LOADING, silent),
    });
  }

  getActive(): Observable<DocumentType[]> {
    return this.http.get<DocumentType[]>(`${this.apiUrl}/active`);
  }

  getById(id: number, silent = false): Observable<DocumentType> {
    return this.http.get<DocumentType>(`${this.apiUrl}/${id}`, {
      context: new HttpContext().set(SKIP_LOADING, silent),
    });
  }

  /** Deshace la eliminación: el tipo vuelve activo */
  restore(id: number): Observable<DocumentType> {
    return this.http.post<DocumentType>(`${this.apiUrl}/${id}/restore`, {});
  }

  create(documentType: DocumentTypeRequest): Observable<DocumentType> {
    return this.http.post<DocumentType>(this.apiUrl, documentType);
  }

  update(id: number, documentType: DocumentTypeRequest): Observable<DocumentType> {
    return this.http.put<DocumentType>(`${this.apiUrl}/${id}`, documentType);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  toggleActive(id: number): Observable<DocumentType> {
    return this.http.patch<DocumentType>(`${this.apiUrl}/${id}/toggle-status`, {});
  }

  activate(id: number): Observable<DocumentType> {
    return this.http.patch<DocumentType>(`${this.apiUrl}/${id}/activate`, {});
  }

  deactivate(id: number): Observable<DocumentType> {
    return this.http.patch<DocumentType>(`${this.apiUrl}/${id}/deactivate`, {});
  }
}