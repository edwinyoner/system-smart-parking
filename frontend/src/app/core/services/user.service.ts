import { Injectable } from "@angular/core";
import { HttpClient, HttpContext, HttpParams } from "@angular/common/http";
import { Observable, catchError, throwError } from "rxjs";
import { SKIP_LOADING } from "../interceptors/loading.interceptor";
import { environment } from "../../../environments/environment";
import {
  User,
  CreateUserRequest,
  UpdateUserRequest,
  UserFilters,
} from "../models/user.model";
import { PaginatedResponse } from "../models/pagination.model";
import { Operator } from "../models/parking/operator.model";

/**
 * Servicio para gestión de usuarios
 * CRUD completo de usuarios del sistema
 */
@Injectable({
  providedIn: "root",
})
export class UserService {
  private readonly API_URL = `${environment.apiUrl}/users`;

  constructor(private http: HttpClient) {}

  // ========== LISTAR USUARIOS ==========

  /**
   * Obtiene lista paginada de usuarios con filtros
   * @param silent true = sin spinner (actualización en segundo plano)
   */
  getUsers(filters?: UserFilters, silent = false): Observable<PaginatedResponse<User>> {
    let params = new HttpParams();

    if (filters) {
      if (filters.search) params = params.set("search", filters.search);
      if (filters.roleId)
        params = params.set("roleId", filters.roleId.toString());
      if (filters.status !== undefined)
        params = params.set("status", filters.status.toString());
      if (filters.emailVerified !== undefined)
        params = params.set("emailVerified", filters.emailVerified.toString());
      if (filters.deleted)
        params = params.set("deleted", "true");
      if (filters.page !== undefined)
        params = params.set("page", filters.page.toString());
      if (filters.size !== undefined)
        params = params.set("size", filters.size.toString());
    }

    return this.http
      .get<PaginatedResponse<User>>(this.API_URL, {
        params,
        context: new HttpContext().set(SKIP_LOADING, silent),
      })
      .pipe(catchError(this.handleError));
  }

  /**
   * Obtiene los datos actuales del usuario autenticado, sin mostrar el spinner
   */
  getMe(): Observable<User> {
    return this.http.get<User>(`${this.API_URL}/me`, {
      context: new HttpContext().set(SKIP_LOADING, true),
    });
  }

  // ========== OBTENER USUARIO POR ID ==========

  /**
   * Obtiene un usuario por su ID
   */
  getUserById(id: number, silent = false): Observable<User> {
    return this.http
      .get<User>(`${this.API_URL}/${id}`, {
        context: new HttpContext().set(SKIP_LOADING, silent),
      })
      .pipe(catchError(this.handleError));
  }

  // ========== CREAR USUARIO ==========

  /**
   * Crea un nuevo usuario
   * Backend espera: { firstName, lastName, email, password, phoneNumber, roles: ["ADMIN"], captchaToken }
   */
  createUser(request: CreateUserRequest): Observable<User> {
    return this.http
      .post<User>(this.API_URL, request)
      .pipe(catchError(this.handleError));
  }

  // ========== ACTUALIZAR USUARIO ==========

  /**
   * Actualiza un usuario existente
   * Backend espera: { firstName, lastName, phoneNumber, status, roles: ["ADMIN"] }
   */
  updateUser(id: number, request: UpdateUserRequest): Observable<User> {
    return this.http
      .put<User>(`${this.API_URL}/${id}`, request)
      .pipe(catchError(this.handleError));
  }

  // ========== ELIMINAR USUARIO ==========

  /**
   * Elimina (soft delete) un usuario
   */
  deleteUser(id: number): Observable<void> {
    return this.http
      .delete<void>(`${this.API_URL}/${id}`)
      .pipe(catchError(this.handleError));
  }

  /**
   * Restaura un usuario eliminado
   */
  restoreUser(id: number): Observable<User> {
    return this.http
      .post<User>(`${this.API_URL}/${id}/restore`, {})
      .pipe(catchError(this.handleError));
  }

  /**
   * Reenvía credenciales por email
   */
  resendCredentials(userId: number, password: string): Observable<any> {
    return this.http.post(
      `${this.API_URL}/${userId}/resend-credentials`,
      { password }, // En el cuerpo: en la URL quedaría en logs
    );
  }

  // ========== MÉTODO - OBTENER OPERADORES ==========

  /**
   * Obtiene todos los usuarios con rol OPERADOR activos
   * Endpoint: GET /auth-service/v1/users/operators
   * Usado para asignar operadores a zonas de parking
   */
  getOperators(): Observable<Operator[]> {
    return this.http
      .get<Operator[]>(`${this.API_URL}/operators`)
      .pipe(catchError(this.handleError));
  }

  // ========== MANEJO DE ERRORES ==========

  private handleError(error: any): Observable<never> {
    console.error("Error en UserService:", error);
    return throwError(() => error);
  }
}