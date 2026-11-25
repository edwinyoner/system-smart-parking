import { HttpContextToken, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { finalize } from 'rxjs';
import { LoadingService } from '../services/loading.service';

/** Marca peticiones en segundo plano que no deben mostrar el spinner */
export const SKIP_LOADING = new HttpContextToken<boolean>(() => false);

/**
 * Interceptor de loading
 * 
 * Muestra/oculta un spinner global mientras se realizan peticiones HTTP
 * 
 * Nota: Requiere crear LoadingService primero
 */
export const loadingInterceptor: HttpInterceptorFn = (req, next) => {
  const loadingService = inject(LoadingService);

  // Verificar si la petición debe mostrar loading
  // Puedes excluir ciertas URLs si es necesario
  const showLoading = !req.url.includes('/no-loading') && !req.context.get(SKIP_LOADING);

  if (showLoading) {
    loadingService.show();
  }

  return next(req).pipe(
    finalize(() => {
      if (showLoading) {
        loadingService.hide();
      }
    })
  );
};