import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';

// Services
import { PermissionService } from '../../../core/services/permission.service';
import { AuthContextService } from '../../../core/services/auth-context.service';

// Models
import { Permission, PermissionFilters } from '../../../core/models/permission.model';
import { PaginatedResponse } from '../../../core/models/pagination.model';

// Components
import { AlertMessageComponent } from '../../../shared/components/alert-message/alert-message.component';
import { takeSuccessMessage } from '../../../core/utils/flash-message.util';

@Component({
  selector: 'app-permission-list',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    AlertMessageComponent
  ],
  templateUrl: './permission-list.component.html',
  styleUrls: ['./permission-list.component.scss']
})
export class PermissionListComponent implements OnInit {
  
  filterForm!: FormGroup;
  permissions: Permission[] = [];
  
  isLoading = false;
  successMessage: string | null = null;
  errorMessage: string | null = null;

  // Paginación
  currentPage = 1;
  pageSize = 10;
  totalItems = 0;
  totalPages = 0;
  pageSizeOptions = [5, 10, 15, 20, 25, 50];

  // Math para el template
  Math = Math;

  // Módulos disponibles
  availableModules = [
    { value: '', label: 'Todos los módulos' },
    { value: 'users', label: 'Usuarios' },
    { value: 'roles', label: 'Roles' },
    { value: 'permissions', label: 'Permisos' },
    { value: 'parking', label: 'Estacionamiento' },
    { value: 'rates', label: 'Tarifas' },
    { value: 'reports', label: 'Reportes' },
    { value: 'sensors', label: 'Sensores' },
    { value: 'cameras', label: 'Cámaras' },
    { value: 'barriers', label: 'Barreras' },
    { value: 'dashboard', label: 'Dashboard' }
  ];

  constructor(
    private fb: FormBuilder,
    private permissionService: PermissionService,
    private authContext: AuthContextService,
    private router: Router
  ) {
    // Capturar mensajes de éxito desde la navegación
    this.successMessage = takeSuccessMessage();
    if (this.successMessage) {
      setTimeout(() => (this.successMessage = null), 5000);
    }
  }

  ngOnInit(): void {
    this.initFilterForm();
    this.loadPermissions();
    this.setupFilterListeners();
  }

  /**
   * Control de permisos (patrón aprendido)
   */
  hasPermission(permission: string): boolean {
    return this.authContext.hasPermission(permission);
  }

  private initFilterForm(): void {
    this.filterForm = this.fb.group({
      search: [''],
      module: [''],
      status: ['']
    });
  }

  private setupFilterListeners(): void {
    // Búsqueda con debounce
    this.filterForm.get('search')?.valueChanges
      .pipe(debounceTime(400), distinctUntilChanged())
      .subscribe(() => {
        this.currentPage = 1;
        this.loadPermissions();
      });

    // Módulo - Sin debounce
    this.filterForm.get('module')?.valueChanges.subscribe(() => {
      this.currentPage = 1;
      this.loadPermissions();
    });

    // Estado - Sin debounce
    this.filterForm.get('status')?.valueChanges.subscribe(() => {
      this.currentPage = 1;
      this.loadPermissions();
    });
  }

  loadPermissions(): void {
    this.isLoading = true;
    // No usar filterForm.value: aquí aún tendría el valor anterior al cambio
    const val = this.filterForm.getRawValue();

    const filters: PermissionFilters = {
      search: val.search || undefined,
      module: val.module || undefined,
      status: val.status !== '' ? val.status === 'true' : undefined,
      page: this.currentPage - 1,
      size: this.pageSize,
      sortBy: 'id',
      sortDirection: 'desc'
    };

    this.permissionService.getPermissions(filters).subscribe({
      next: (res: PaginatedResponse<Permission>) => {
        this.permissions = res.content;
        this.totalItems = res.totalElements;
        this.totalPages = res.totalPages;
        this.currentPage = res.number + 1;
        this.isLoading = false;
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = 'Error al cargar los permisos del sistema';
        console.error(err);
      }
    });
  }

  clearFilters(): void {
    this.filterForm.reset({
      search: '',
      module: '',
      status: ''
    }, { emitEvent: false });
    this.currentPage = 1;
    this.loadPermissions();
  }

  // ========== NAVEGACIÓN ==========

  onPageSizeChange(event: any): void {
    this.pageSize = Number(event.target.value);
    this.currentPage = 1;
    this.loadPermissions();
  }

  goToPage(page: number): void {
    if (page >= 1 && page <= this.totalPages) {
      this.currentPage = page;
      this.loadPermissions();
    }
  }

  getPages(): number[] {
    const pages = [];
    const maxVisiblePages = 5;
    let startPage = Math.max(1, this.currentPage - Math.floor(maxVisiblePages / 2));
    let endPage = Math.min(this.totalPages, startPage + maxVisiblePages - 1);

    if (endPage - startPage + 1 < maxVisiblePages) {
      startPage = Math.max(1, endPage - maxVisiblePages + 1);
    }

    for (let i = startPage; i <= endPage; i++) {
      pages.push(i);
    }
    return pages;
  }

  viewPermission(id: number): void {
    this.router.navigate(['/permissions', id]);
  }

  // ========== UTILIDADES ==========

  formatDate(date: string | Date): string {
    if (!date) return 'N/A';
    return new Date(date).toLocaleDateString('es-PE', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    });
  }

  getModuleLabel(moduleValue: string): string {
    const module = this.availableModules.find(m => m.value === moduleValue);
    return module?.label || moduleValue || 'Sin módulo';
  }

  getModuleBadgeClass(moduleValue: string): string {
    const colors: { [key: string]: string } = {
      'users': 'bg-primary',
      'roles': 'bg-success',
      'permissions': 'bg-warning',
      'parking': 'bg-info',
      'rates': 'bg-secondary',
      'reports': 'bg-dark',
      'sensors': 'bg-danger',
      'cameras': 'bg-primary',
      'barriers': 'bg-success',
      'dashboard': 'bg-info'
    };
    return colors[moduleValue] || 'bg-secondary';
  }

  dismissSuccess(): void {
    this.successMessage = null;
  }

  dismissError(): void {
    this.errorMessage = null;
  }
}