import { Injectable } from '@angular/core';
import { HttpClient, HttpContext, HttpHeaders } from '@angular/common/http';
import { Observable, BehaviorSubject, tap, catchError, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { SKIP_LOADING } from '../../interceptors/loading.interceptor';
import { environment } from '../../../../environments/environment';
import { TokenService } from './token.service';
import { CurrentUserService, CurrentUserProfile } from './current-user.service';
import { 
  AuthResponse, 
  RefreshTokenResponse, 
  VerifyEmailResponse,
  ForgotPasswordResponse,
  ResetPasswordResponse
} from '../../models/auth-service/auth-response.model';
import { 
  LoginRequest, 
  ChangePasswordRequest,
  ForgotPasswordRequest,
  ResetPasswordRequest,
  ResendVerificationRequest
} from '../../models/auth-service/login-request.model';

/**
 * Servicio de autenticación
 * Maneja login, logout, registro y recuperación de contraseña
 */
@Injectable({
  providedIn: 'root'
})
export class AuthService {

  private readonly API_URL = `${environment.apiUrl}/auth`;  // ← Agregar /auth
  
    // Observable del estado de autenticación
  private isAuthenticatedSubject = new BehaviorSubject<boolean>(false);
  public isAuthenticated$ = this.isAuthenticatedSubject.asObservable();

  constructor(
    private http: HttpClient,
    private tokenService: TokenService,
    private currentUserService: CurrentUserService,
    private router: Router
  ) {
    // Ahora sí podemos usar tokenService porque ya fue inyectado
    this.isAuthenticatedSubject.next(this.tokenService.isAuthenticated());

    // Verificar autenticación al iniciar
    this.checkAuthentication();
  }

  // ========== LOGIN ==========

  /**
   * Inicia sesión con email y contraseña
   */
  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.API_URL}/login`, request).pipe(
      tap(response => {
        this.handleAuthResponse(response);
      }),
      catchError(this.handleError)
    );
  }

  /**
   * Nombres de los roles activos con los que se puede iniciar sesión
   */
  getLoginRoles(): Observable<string[]> {
    return this.http.get<string[]>(`${this.API_URL}/login-roles`, {
      context: new HttpContext().set(SKIP_LOADING, true)
    });
  }

  /**
   * Maneja la respuesta de autenticación exitosa
   */
  private handleAuthResponse(response: AuthResponse): void {
    // Guardar tokens
    this.tokenService.saveTokens(response.accessToken, response.refreshToken);

    // Cargar los datos del usuario que acaba de entrar
    this.currentUserService.refreshCurrentUser();
    
    // Actualizar estado de autenticación
    this.isAuthenticatedSubject.next(true);
    
    // Debug info
    if (environment.features.showDebugInfo) {
      console.log('Login exitoso:', {
        user: response.user.email,
        roles: response.user.roles,
        expiresIn: `${response.expiresIn / 1000 / 60} minutos`
      });
    }
  }

  // ========== LOGOUT ==========

  /**
   * Cierra sesión del usuario actual
   */
  logout(): void {
    // Opcional: Llamar al backend para invalidar el token
    // this.http.post(`${this.API_URL}/logout`, {}).subscribe();
    
    // Limpiar tokens
    this.tokenService.clearTokens();

    // Evitar que el siguiente usuario herede datos o roles de este
    this.currentUserService.clearCurrentUser();
    
    // Actualizar estado
    this.isAuthenticatedSubject.next(false);
    
    // Redirigir al login
    this.router.navigate(['/login']);
    
    if (environment.features.showDebugInfo) {
      console.log('Logout exitoso');
    }
  }

  // ========== REFRESH TOKEN ==========

  /**
   * Renueva el access token usando el refresh token
   */
  refreshToken(): Observable<RefreshTokenResponse> {
    const refreshToken = this.tokenService.getRefreshToken();
    
    if (!refreshToken) {
      return throwError(() => new Error('No refresh token available'));
    }

    return this.http.post<RefreshTokenResponse>(`${this.API_URL}/refresh`, { refreshToken }).pipe(
      tap(response => {
        this.tokenService.saveTokens(response.accessToken, response.refreshToken);
        this.currentUserService.refreshCurrentUser();
        this.isAuthenticatedSubject.next(true);
      }),
      catchError(error => {
        this.logout();
        return throwError(() => error);
      })
    );
  }

  // ========== VERIFICACIÓN DE EMAIL ==========

  /**
   * Verifica el email del usuario con el token recibido por email
   */
  verifyEmail(token: string): Observable<VerifyEmailResponse> {
    return this.http.get<VerifyEmailResponse>(`${this.API_URL}/verify-email?token=${token}`).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Reenvía el email de verificación
   */
  resendVerification(request: ResendVerificationRequest): Observable<any> {
    return this.http.post(`${this.API_URL}/resend-verification`, request).pipe(
      catchError(this.handleError)
    );
  }

  // ========== RECUPERACIÓN DE CONTRASEÑA ==========

  /**
   * Solicita recuperación de contraseña
   */
  forgotPassword(request: ForgotPasswordRequest): Observable<ForgotPasswordResponse> {
    return this.http.post<ForgotPasswordResponse>(`${this.API_URL}/forgot-password`, request).pipe(
      catchError(this.handleError)
    );
  }

  /**
 * Actualiza el perfil del usuario actual
 */
updateProfile(data: {firstName: string, lastName: string, phoneNumber?: string | null, profilePicture?: string | null}): Observable<CurrentUserProfile> {
  return this.http.put<CurrentUserProfile>(`${this.API_URL}/profile`, data);
}

  /**
   * Restablece la contraseña con el token recibido por email
   */
  resetPassword(request: ResetPasswordRequest): Observable<ResetPasswordResponse> {
    return this.http.post<ResetPasswordResponse>(`${this.API_URL}/reset-password`, request).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Cambia la contraseña del usuario autenticado
   */
  changePassword(request: ChangePasswordRequest): Observable<any> {
    return this.http.post(`${this.API_URL}/change-password`, request).pipe(
      catchError(this.handleError)
    );
  }

  // ========== UTILIDADES ==========

  /**
   * Verifica el estado de autenticación actual
   */
  checkAuthentication(): void {
    const isAuth = this.tokenService.isAuthenticated();
    this.isAuthenticatedSubject.next(isAuth);
    
    if (!isAuth && this.tokenService.hasToken()) {
      // Token expirado, intentar refresh
      const refreshToken = this.tokenService.getRefreshToken();
      if (refreshToken) {
        this.refreshToken().subscribe({
          error: () => this.logout()
        });
      }
    }
  }

  /**
   * Verifica si el usuario está autenticado (síncrono)
   */
  isLoggedIn(): boolean {
    return this.tokenService.isAuthenticated();
  }

  /**
   * Manejo de errores HTTP
   */
  private handleError(error: any): Observable<never> {
    let errorMessage = 'Ha ocurrido un error';

    if (error.error instanceof ErrorEvent) {
      // Error del cliente
      errorMessage = `Error: ${error.error.message}`;
    } else {
      // Error del servidor
      errorMessage = error.error?.message || error.message || errorMessage;
    }

    console.error('Error en AuthService:', errorMessage);
    return throwError(() => ({ status: error.status, message: errorMessage, error }));
  }
}