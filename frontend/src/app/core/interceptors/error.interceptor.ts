import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { TokenService } from '../services/token.service';
import { CurrentUserService } from '../services/current-user.service';
import { environment } from '../../../environments/environment';

/**
 * Interceptor de errores HTTP
 * 
 * Maneja errores HTTP de forma centralizada:
 * - 401 Unauthorized → Logout y redirigir a login
 * - 403 Forbidden → Mostrar mensaje de acceso denegado
 * - 404 Not Found → Mostrar mensaje de recurso no encontrado
 * - 500 Server Error → Mostrar mensaje de error del servidor
 * - Network Error → Mostrar mensaje de error de conexión
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const tokenService = inject(TokenService);
  const currentUserService = inject(CurrentUserService);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      let errorMessage = 'Ha ocurrido un error';

      if (error.error instanceof ErrorEvent) {
        // Error del lado del cliente (network error)
        errorMessage = `Error de conexión: ${error.error.message}`;
        console.error('❌ Error del cliente:', error.error);
      } else {
        // Error del lado del servidor
        switch (error.status) {
          case 401:
            // No autorizado - Token inválido o expirado
            errorMessage = error.error?.message || 'Sesión expirada. Por favor, inicia sesión nuevamente.';
            handleUnauthorized(tokenService, currentUserService, router, error.error?.code);
            break;

          case 403:
            // Prohibido - Sin permisos
            errorMessage = error.error?.message || 'No tienes permisos para realizar esta acción.';
            console.warn('🔒 Acceso denegado:', errorMessage);
            break;

          case 404:
            // No encontrado
            errorMessage = error.error?.message || 'Recurso no encontrado.';
            console.warn('🔍 Recurso no encontrado:', req.url);
            break;

          case 422:
            // Error de validación
            errorMessage = error.error?.message || 'Los datos enviados no son válidos.';
            console.warn('⚠️ Error de validación:', error.error);
            break;

          case 500:
            // Error del servidor
            errorMessage = error.error?.message || 'Error interno del servidor. Por favor, intenta más tarde.';
            console.error('💥 Error del servidor:', error);
            break;

          case 503:
            // Servicio no disponible
            errorMessage = 'Servicio temporalmente no disponible. Por favor, intenta más tarde.';
            console.error('🚫 Servicio no disponible');
            break;

          default:
            // Otros errores
            errorMessage = error.error?.message || `Error ${error.status}: ${error.statusText}`;
            console.error(`❌ Error HTTP ${error.status}:`, error);
        }
      }

      if (environment.features.showDebugInfo) {
        console.error('🔴 ErrorInterceptor:', {
          status: error.status,
          message: errorMessage,
          url: req.url,
          error: error.error
        });
      }

      // Retornar el error con mensaje formateado
      return throwError(() => ({
        status: error.status,
        message: errorMessage,
        error: error.error,
        timestamp: new Date().toISOString()
      }));
    })
  );
};

/**
 * Maneja errores 401 (Unauthorized)
 */
function handleUnauthorized(
  tokenService: TokenService,
  currentUserService: CurrentUserService,
  router: Router,
  reason?: string
): void {
  // Sin token no hay sesión que cerrar: es un login fallido, o ya se cerró por
  // otra petición y volver a navegar taparía el motivo
  if (!tokenService.hasToken()) {
    return;
  }

  console.warn('🔒 Token inválido o expirado, cerrando sesión...');
  
  // Limpiar tokens y datos del usuario
  tokenService.clearTokens();
  currentUserService.clearCurrentUser();
  
  // Redirigir al login
  router.navigate(['/login'], {
    queryParams: { 
      sessionExpired: 'true',
      reason: reason ?? null
    }
  });
}