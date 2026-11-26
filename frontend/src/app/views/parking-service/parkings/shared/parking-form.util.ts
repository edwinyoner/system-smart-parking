// src/app/views/parking-service/parkings/shared/parking-form.util.ts
import { AbstractControl, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Observable, catchError, map, of } from 'rxjs';
import { UserService } from '../../../../core/services/auth-service/user.service';
import { PARKING_CODE_PATTERN } from '../../../../core/utils/parking.util';

/** Formulario de datos del parqueo, compartido por el asistente y la edición */
export function buildParkingForm(fb: FormBuilder, withCode: boolean): FormGroup {
  const group: Record<string, any> = {
    name:        ['', [Validators.required, Validators.maxLength(100)]],
    address:     ['', [Validators.required, Validators.maxLength(255)]],
    description: ['', Validators.maxLength(500)],
    latitude:    [null, [Validators.min(-90), Validators.max(90)]],
    longitude:   [null, [Validators.min(-180), Validators.max(180)]],
    managerId:   [null],
  };
  if (withCode) {
    group['code'] = ['', [Validators.required, Validators.pattern(PARKING_CODE_PATTERN)]];
  }
  return fb.group(group, { validators: coordinatesTogether });
}

/** Latitud y longitud van juntas (igual que el backend) */
function coordinatesTogether(form: AbstractControl) {
  const lat = form.get('latitude')?.value;
  const lng = form.get('longitude')?.value;
  const hasLat = lat !== null && lat !== '' && lat !== undefined;
  const hasLng = lng !== null && lng !== '' && lng !== undefined;
  return hasLat !== hasLng ? { coordinates: true } : null;
}

export function fieldError(form: FormGroup, field: string): string {
  const ctrl = form.get(field);
  if (!ctrl || !ctrl.errors || !ctrl.touched) return '';
  if (ctrl.errors['required'])  return 'Este campo es obligatorio.';
  if (ctrl.errors['maxlength']) return `Máximo ${ctrl.errors['maxlength'].requiredLength} caracteres.`;
  if (ctrl.errors['pattern'])   return 'De 2 a 20 caracteres: mayúsculas, números, guion y guion bajo; empieza con letra o número.';
  if (ctrl.errors['min'] || ctrl.errors['max']) return 'Valor fuera de rango.';
  return 'Campo inválido.';
}

/** Número o undefined (los inputs number vacíos llegan como null o '') */
export function numberOrUndefined(value: any): number | undefined {
  return value === null || value === '' || value === undefined ? undefined : Number(value);
}

export interface ManagerOption {
  id: number;
  name: string;
  email: string;
}

/**
 * Usuarios activos que pueden ser responsables. Exige users.read: sin ese permiso
 * devuelve null y el selector se muestra deshabilitado.
 */
export function loadManagerOptions(userService: UserService): Observable<ManagerOption[] | null> {
  return userService.getUsers({ status: true, page: 0, size: 200 }, true).pipe(
    map(page => page.content.map(u => ({
      id: u.id,
      name: u.fullName || `${u.firstName} ${u.lastName}`.trim(),
      email: u.email,
    })).sort((a, b) => a.name.localeCompare(b.name))),
    catchError(() => of(null)),
  );
}
