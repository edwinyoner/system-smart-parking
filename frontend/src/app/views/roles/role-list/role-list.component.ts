import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { EMPTY, catchError, debounceTime, exhaustMap, filter, merge } from 'rxjs';
import { liveRefresh } from '../../../core/utils/live-refresh.util';

// Services
import { RoleService } from '../../../core/services/role.service';
import { AuthContextService } from '../../../core/services/auth-context.service';

// Models
import { Role, RoleFilters } from '../../../core/models/role.model';
import { PaginatedResponse } from '../../../core/models/pagination.model';

// Components
import { AlertMessageComponent } from '../../../shared/components/alert-message/alert-message.component';
import { takeSuccessMessage } from '../../../core/utils/flash-message.util';

@Component({
  selector: 'app-role-list',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    AlertMessageComponent
  ],
  templateUrl: './role-list.component.html',
  styleUrls: ['./role-list.component.css']
})
export class RoleListComponent implements OnInit {
  
  filterForm!: FormGroup;
  roles: Role[] = [];
  isLoading = false;
  successMessage: string | null = null;
  errorMessage: string | null = null;

  // Paginación
  currentPage = 1;
  pageSize = 10;
  totalItems = 0;
  totalPages = 0;
  pageSizeOptions = [5, 10, 15, 20, 25, 50];

  // Confirmación de eliminación
  showDeleteConfirm = false;
  roleToDelete: Role | null = null;

  // Filtros vigentes. Son distintos de los del formulario mientras se escribe
  appliedFilters: RoleFilters = {};

  // false hasta la primera respuesta
  loaded = false;

  // Identifica la última carga pedida, para descartar respuestas atrasadas
  private lastRequestId = 0;
  private destroyRef = inject(DestroyRef);

  // Math para el template
  Math = Math;

  constructor(
    private fb: FormBuilder,
    private roleService: RoleService,
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
    this.loadRoles();
    this.setupFilterListeners();
    this.startLiveRefresh();
  }

  /**
   * Control de permisos (patrón aprendido)
   */
  hasPermission(permission: string): boolean {
    return this.authContext.hasPermission(permission);
  }

  /** Solo quien entró como ADMIN modifica roles */
  get isAdmin(): boolean {
    return this.authContext.isActiveRole('ADMIN');
  }

  private initFilterForm(): void {
    this.filterForm = this.fb.group({
      search: [''],
      status: ['']
    });
  }

  private setupFilterListeners(): void {
    const { search, status } = this.filterForm.controls;

    merge(
      search.valueChanges.pipe(debounceTime(400)),
      status.valueChanges
    ).pipe(
      filter(() => JSON.stringify(this.readFilters()) !== JSON.stringify(this.appliedFilters)),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => this.applyFilters());
  }

  /** Vuelve a la primera página con los filtros del formulario */
  applyFilters(): void {
    this.appliedFilters = this.readFilters();
    this.currentPage = 1;
    this.loadRoles();
  }

  loadRoles(): void {
    this.isLoading = true;
    this.errorMessage = null;
    const requestId = ++this.lastRequestId;

    this.roleService.getRoles(this.buildQuery(), true).subscribe({
      next: (res) => {
        if (requestId !== this.lastRequestId) return;
        this.isLoading = false;
        this.showPage(res);
      },
      error: (err) => {
        if (requestId !== this.lastRequestId) return;
        this.isLoading = false;
        this.errorMessage = 'Error al cargar los roles del sistema';
        console.error(err);
      }
    });
  }

  /**
   * Mantiene la tabla al día sin spinner ni recargar la página
   */
  private startLiveRefresh(): void {
    liveRefresh(5000).pipe(
      filter(() => !this.isLoading),
      exhaustMap(() => {
        // Si mientras tanto cambia la página o un filtro, esta respuesta se descarta
        const requestId = this.lastRequestId;
        return this.roleService.getRoles(this.buildQuery(), true).pipe(
          filter(() => requestId === this.lastRequestId),
          catchError(() => EMPTY)
        );
      }),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(res => this.showPage(res));
  }

  private readFilters(): RoleFilters {
    // No usar filterForm.value: aquí aún tendría el valor anterior al cambio
    const val = this.filterForm.getRawValue();

    return {
      search: val.search.trim() || undefined,
      status: val.status !== '' ? val.status === 'true' : undefined
    };
  }

  private buildQuery(): RoleFilters {
    return { ...this.appliedFilters, page: this.currentPage - 1, size: this.pageSize };
  }

  private showPage(res: PaginatedResponse<Role>): void {
    this.loaded = true;
    this.totalItems = res.totalElements;
    this.totalPages = res.totalPages;

    // La página quedó vacía: ir a la última
    if (res.totalPages > 0 && this.currentPage > res.totalPages) {
      this.currentPage = res.totalPages;
      this.loadRoles();
      return;
    }

    this.roles = res.content;
  }

  /** Cantidad de filtros aplicados */
  get activeFilterCount(): number {
    return Object.values(this.appliedFilters).filter(value => value !== undefined).length;
  }

  clearSearch(): void {
    this.filterForm.patchValue({ search: '' }, { emitEvent: false });
    this.applyFilters();
  }

  clearFilters(): void {
    // Sin emitir eventos: se consulta una sola vez
    this.filterForm.reset({ search: '', status: '' }, { emitEvent: false });
    this.applyFilters();
  }

  /** Motivo por el que no se puede eliminar, o null. Quien lo hace cumplir es auth-service */
  getDeleteBlockedReason(role: Role): string | null {
    if (role.systemRole) {
      return 'Es un rol del sistema: no se puede eliminar.';
    }
    if (role.userCount) {
      const users = role.userCount === 1 ? '1 usuario' : `${role.userCount} usuarios`;
      return `Está asignado a ${users}: quítalo antes de eliminarlo.`;
    }
    return null;
  }

  // ========== NAVEGACIÓN Y EVENTOS ==========

  onPageSizeChange(event: any): void {
    this.pageSize = Number(event.target.value);
    this.currentPage = 1;
    this.loadRoles();
  }

  goToPage(page: number): void {
    if (page >= 1 && page <= this.totalPages && page !== this.currentPage) {
      this.currentPage = page;
      this.loadRoles();
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

  createRole(): void {
    this.router.navigate(['/roles/create']);
  }

  viewRole(id: number): void {
    this.router.navigate(['/roles', id]);
  }

  editRole(id: number): void {
    this.router.navigate(['/roles', id, 'edit']);
  }

  confirmDelete(role: Role): void {
    this.roleToDelete = role;
    this.showDeleteConfirm = true;
  }

  cancelDelete(): void {
    this.showDeleteConfirm = false;
    this.roleToDelete = null;
  }

  deleteRole(): void {
    if (!this.roleToDelete) return;
    this.isLoading = true;

    this.roleService.deleteRole(this.roleToDelete.id).subscribe({
      next: () => {
        this.isLoading = false;
        this.successMessage = `Rol "${this.roleToDelete?.name}" eliminado exitosamente`;
        this.showDeleteConfirm = false;
        this.roleToDelete = null;
        this.loadRoles();
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err.error?.message || 'No se pudo eliminar el rol';
        this.cancelDelete();
      }
    });
  }

  // ========== FORMATEADORES ==========

  getPermissionCount(role: Role): number {
    return role.permissions?.length || 0;
  }

  formatDate(date: string | Date): string {
    if (!date) return 'N/A';
    return new Date(date).toLocaleDateString('es-PE', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    });
  }

  dismissSuccess(): void {
    this.successMessage = null;
  }

  dismissError(): void {
    this.errorMessage = null;
  }
}