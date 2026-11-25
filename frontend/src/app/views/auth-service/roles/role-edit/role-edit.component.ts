import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';

// Services
import { RoleService } from '../../../../core/services/auth-service/role.service';
import { PermissionService } from '../../../../core/services/auth-service/permission.service';
import { AuthContextService } from '../../../../core/services/auth-service/auth-context.service';

// Models
import { Role, UpdateRoleRequest } from '../../../../core/models/auth-service/role.model';
import { PermissionGroup } from '../../../../core/models/auth-service/permission.model';

// Components
import { AlertMessageComponent } from '../../../../shared/components/alert-message/alert-message.component';
import { PermissionSelectorComponent } from '../permission-selector/permission-selector.component';

@Component({
  selector: 'app-role-edit',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    AlertMessageComponent,
    PermissionSelectorComponent
  ],
  templateUrl: './role-edit.component.html',
  styleUrls: ['./role-edit.component.css']
})
export class RoleEditComponent implements OnInit {
  
  roleForm!: FormGroup;
  permissionGroups: PermissionGroup[] = [];
  selectedPermissions: Set<number> = new Set();
  
  roleId!: number;
  role: Role | null = null;
  
  isLoading = false;
  isLoadingRole = false;
  isLoadingPermissions = false;
  errorMessage: string | null = null;
  
  constructor(
    private fb: FormBuilder,
    private roleService: RoleService,
    private permissionService: PermissionService,
    private authContext: AuthContextService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.initForm();
    this.getRoleId();
    this.loadRole();
    this.loadPermissions();
  }

  /**
   * Control de permisos (patrón aprendido)
   */
  hasPermission(permission: string): boolean {
    return this.authContext.hasPermission(permission);
  }

  private getRoleId(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.roleId = Number(id);
    } else {
      this.router.navigate(['/roles']);
    }
  }

  private initForm(): void {
    this.roleForm = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(50)]],
      description: ['', [Validators.maxLength(255)]],
      status: [true]
    });
  }

  private loadRole(): void {
    this.isLoadingRole = true;
    
    this.roleService.getRoleById(this.roleId).subscribe({
      next: (role) => {
        this.role = role;
        this.populateForm(role);
        this.isLoadingRole = false;
      },
      error: (error) => {
        this.isLoadingRole = false;
        this.errorMessage = 'Error al cargar el rol';
        console.error('Error loading role:', error);
      }
    });
  }

  private populateForm(role: Role): void {
    this.roleForm.patchValue({
      name: role.name,
      description: role.description || '',
      status: role.status
    });

    // Un control deshabilitado no se valida ni se puede editar
    if (role.systemRole) {
      this.roleForm.disable();
    }

    // Pre-seleccionar permisos del rol
    if (role.permissions && role.permissions.length > 0) {
      role.permissions.forEach(permission => {
        this.selectedPermissions.add(permission.id);
      });
    }
  }

  private loadPermissions(): void {
    this.isLoadingPermissions = true;
    
    this.permissionService.getPermissionsGrouped().subscribe({
      next: (groups) => {
        this.permissionGroups = groups;
        this.isLoadingPermissions = false;
      },
      error: (error) => {
        this.isLoadingPermissions = false;
        this.errorMessage = 'Error al cargar los permisos del sistema';
        console.error('Error loading permissions:', error);
      }
    });
  }

  // ========== GESTIÓN DE PERMISOS ==========

  /**
   * Obtiene el conteo de permisos seleccionados
   */
  getSelectedPermissionsCount(): number {
    return this.selectedPermissions.size;
  }

  // ========== FORMULARIO ==========

  onSubmit(): void {
    // Validar formulario
    if (!this.role?.permissionsEditable) {
      return;
    }

    if (this.roleForm.invalid) {
      this.markFormGroupTouched(this.roleForm);
      return;
    }

    // Validar que se haya seleccionado al menos un permiso
    if (this.selectedPermissions.size === 0) {
      this.errorMessage = 'Debes seleccionar al menos un permiso para el rol';
      return;
    }

    this.isLoading = true;
    this.errorMessage = null;

    const { name, description, status } = this.roleForm.getRawValue();
    const request: UpdateRoleRequest = { permissionIds: Array.from(this.selectedPermissions) };

    // De un rol del sistema solo se envían los permisos
    if (!this.isSystemRole()) {
      request.name = name.trim();
      request.description = description?.trim() || '';
      request.status = status;
    }

    this.roleService.updateRole(this.roleId, request).subscribe({
      next: (role) => {
        this.isLoading = false;
        
        // Navegar a la lista con mensaje de éxito
        this.router.navigate(['/roles'], {
          state: {
            successMessage: `Rol "${role.name}" actualizado exitosamente con ${this.selectedPermissions.size} permisos`
          }
        });
      },
      error: (error) => {
        this.isLoading = false;
        this.errorMessage = error.error?.message || 'Error al actualizar el rol';
        window.scrollTo({ top: 0 });
        console.error('Error updating role:', error);
      }
    });
  }

  cancel(): void {
    this.router.navigate(['/roles']);
  }

  private markFormGroupTouched(formGroup: FormGroup): void {
    Object.keys(formGroup.controls).forEach(key => {
      const control = formGroup.get(key);
      control?.markAsTouched();

      if (control instanceof FormGroup) {
        this.markFormGroupTouched(control);
      }
    });
  }

  // ========== VALIDACIONES ==========

  hasError(field: string, error: string): boolean {
    const control = this.roleForm.get(field);
    return !!(control?.hasError(error) && control?.touched);
  }

  getErrorMessage(field: string): string {
    const control = this.roleForm.get(field);

    if (control?.hasError('required')) {
      return 'Este campo es requerido';
    }

    if (control?.hasError('minlength')) {
      const minLength = control.errors?.['minlength'].requiredLength;
      return `Mínimo ${minLength} caracteres`;
    }

    if (control?.hasError('maxlength')) {
      const maxLength = control.errors?.['maxlength'].requiredLength;
      return `Máximo ${maxLength} caracteres`;
    }

    return '';
  }

  dismissError(): void {
    this.errorMessage = null;
  }

  /**
   * Verifica si el rol es del sistema
   */
  isSystemRole(): boolean {
    return !!this.role?.systemRole;
  }
}