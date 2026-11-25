import { Component, DestroyRef, OnInit, inject } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { CommonModule } from "@angular/common";
import { FormBuilder, FormGroup, ReactiveFormsModule } from "@angular/forms";
import { Router } from "@angular/router";
import { EMPTY, catchError, debounceTime, exhaustMap, filter, merge } from "rxjs";
import { liveRefresh } from "../../../core/utils/live-refresh.util";

// Services
import { UserService } from "../../../core/services/user.service";
import { RoleService } from "../../../core/services/role.service";
import { UserProtectionService } from "../../../core/services/user-protection.service";
import { AuthContextService } from "../../../core/services/auth-context.service";

// Models
import { User, UserFilters } from "../../../core/models/user.model";
import { Role } from "../../../core/models/role.model";
import { PaginatedResponse } from "../../../core/models/pagination.model";

// Components
import { AlertMessageComponent } from "../../../shared/components/alert-message/alert-message.component";
import { takeSuccessMessage } from "../../../core/utils/flash-message.util";

@Component({
  selector: "app-user-list",
  standalone: true,
  imports: [
    CommonModule, 
    ReactiveFormsModule, 
    AlertMessageComponent
  ],
  templateUrl: "./user-list.component.html",
  styleUrls: ["./user-list.component.css"],
})
export class UserListComponent implements OnInit {
  filterForm!: FormGroup;
  users: User[] = [];
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
  userToDelete: User | null = null;
  deleteBlockedReason: string | null = null;

  // Papelera: muestra solo los usuarios eliminados
  showDeleted = false;

  // Confirmación de restauración
  showRestoreConfirm = false;
  userToRestore: User | null = null;
  restoreBlockedReason: string | null = null;

  // Filtros vigentes. Son distintos de los del formulario mientras se escribe
  appliedFilters: UserFilters = {};

  // false hasta la primera respuesta
  loaded = false;

  // Identifica la última carga pedida, para descartar respuestas atrasadas
  private lastRequestId = 0;
  private destroyRef = inject(DestroyRef);

  // Math para el template
  Math = Math;

  constructor(
    private fb: FormBuilder,
    private userService: UserService,
    private roleService: RoleService,
    private router: Router,
    private authContext: AuthContextService,
    private userProtection: UserProtectionService
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
    this.loadUsers();
    this.setupFilterListeners();
    this.startLiveRefresh();
  }

  /**
   * MODO DE USO EN HTML: @if (hasPermission('users.create'))
   */
  hasPermission(permission: string): boolean {
    return this.authContext.hasPermission(permission);
  }

  /** A un ADMIN solo lo edita quien entró como ADMIN */
  canEdit(user: User): boolean {
    return this.userProtection.canEdit(user);
  }

  private initFilterForm(): void {
    this.filterForm = this.fb.group({
      search: [""],
      roleId: [""],
      status: [""],
      emailVerified: [""],
    });
  }

  private setupFilterListeners(): void {
    const { search, roleId, status, emailVerified } = this.filterForm.controls;

    merge(
      search.valueChanges.pipe(debounceTime(400)),
      roleId.valueChanges,
      status.valueChanges,
      emailVerified.valueChanges
    ).pipe(
      // Limpiar los filtros o volver al mismo texto no repite la consulta
      filter(() => JSON.stringify(this.readFilters()) !== JSON.stringify(this.appliedFilters)),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => this.applyFilters());
  }

  /** Vuelve a la primera página con los filtros del formulario */
  applyFilters(): void {
    this.appliedFilters = this.readFilters();
    this.currentPage = 1;
    this.loadUsers();
  }

  private loadRoles(): void {
    this.roleService.getAllRoles().subscribe({
      next: (roles) => (this.roles = roles),
      error: (err) => {
        this.errorMessage = "No se pudieron cargar los roles del filtro";
        console.error(err);
      }
    });
  }

  loadUsers(): void {
    this.isLoading = true;
    this.errorMessage = null;
    const requestId = ++this.lastRequestId;

    // Sin spinner global: la tabla se atenúa y se puede seguir escribiendo
    this.userService.getUsers(this.buildQuery(), true).subscribe({
      next: (res) => {
        if (requestId !== this.lastRequestId) return;
        this.isLoading = false;
        this.showPage(res);
      },
      error: (err) => {
        if (requestId !== this.lastRequestId) return;
        this.isLoading = false;
        this.errorMessage = "Error al cargar los usuarios del sistema";
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
        return this.userService.getUsers(this.buildQuery(), true).pipe(
          filter(() => requestId === this.lastRequestId),
          catchError(() => EMPTY)
        );
      }),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(res => this.showPage(res));
  }

  private readFilters(): UserFilters {
    // No usar filterForm.value: aquí aún tendría el valor anterior al cambio
    const val = this.filterForm.getRawValue();

    return {
      search: val.search.trim() || undefined,
      roleId: val.roleId ? Number(val.roleId) : undefined,
      status: val.status !== "" ? val.status === "true" : undefined,
      emailVerified: val.emailVerified !== "" ? val.emailVerified === "true" : undefined
    };
  }

  private buildQuery(): UserFilters {
    return {
      ...this.appliedFilters,
      deleted: this.showDeleted || undefined,
      page: this.currentPage - 1,
      size: this.pageSize
    };
  }

  /** Cambia entre la lista normal y la papelera, conservando los filtros */
  setShowDeleted(deleted: boolean): void {
    if (this.showDeleted === deleted) return;
    this.showDeleted = deleted;
    this.currentPage = 1;
    this.users = [];
    this.loaded = false;
    this.loadUsers();
  }

  private showPage(res: PaginatedResponse<User>): void {
    this.loaded = true;
    this.totalItems = res.totalElements;
    this.totalPages = res.totalPages;

    // La página quedó vacía (p. ej. se eliminó su último usuario): ir a la última
    if (res.totalPages > 0 && this.currentPage > res.totalPages) {
      this.currentPage = res.totalPages;
      this.loadUsers();
      return;
    }

    this.users = res.content;
  }

  /** Cantidad de filtros aplicados */
  get activeFilterCount(): number {
    return Object.values(this.appliedFilters).filter(value => value !== undefined).length;
  }

  clearSearch(): void {
    this.filterForm.patchValue({ search: "" }, { emitEvent: false });
    this.applyFilters();
  }

  clearFilters(): void {
    // Sin emitir eventos: se consulta una sola vez
    this.filterForm.reset({ search: "", roleId: "", status: "", emailVerified: "" }, { emitEvent: false });
    this.applyFilters();
  }

  // --- Navegación y Eventos ---

  onPageSizeChange(event: any): void {
    this.pageSize = Number(event.target.value);
    this.currentPage = 1;
    this.loadUsers();
  }

  goToPage(page: number): void {
    if (page >= 1 && page <= this.totalPages && page !== this.currentPage) {
      this.currentPage = page;
      this.loadUsers();
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

  createUser(): void { this.router.navigate(["/users/create"]); }
  viewUser(id: number): void { this.router.navigate(["/users", id]); }
  editUser(id: number): void { this.router.navigate(["/users", id, "edit"]); }

  confirmDelete(user: User): void {
    this.userToDelete = user;
    this.deleteBlockedReason = this.userProtection.getBlockedReason(user);
    this.showDeleteConfirm = true;
  }

  cancelDelete(): void {
    this.showDeleteConfirm = false;
    this.userToDelete = null;
  }

  deleteUser(): void {
    if (!this.userToDelete) return;
    this.isLoading = true;
    
    this.userService.deleteUser(this.userToDelete.id).subscribe({
      next: () => {
        this.isLoading = false;
        this.successMessage = `Usuario "${this.userToDelete?.firstName}" eliminado exitosamente`;
        this.showDeleteConfirm = false;
        this.userToDelete = null;
        this.loadUsers();
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err.error?.message || "No se pudo eliminar el usuario";
        this.showDeleteConfirm = false;
      }
    });
  }

  confirmRestore(user: User): void {
    this.userToRestore = user;
    // Misma regla que el backend: a un ADMIN solo lo restaura quien entró como ADMIN
    const isAdmin = user.roles?.some(role => role.name === 'ADMIN');
    this.restoreBlockedReason = isAdmin && !this.userProtection.canManageAdmins()
      ? 'Este usuario tiene el rol ADMIN: solo otro usuario con el rol ADMIN activo puede restaurarlo.'
      : null;
    this.showRestoreConfirm = true;
  }

  cancelRestore(): void {
    this.showRestoreConfirm = false;
    this.userToRestore = null;
  }

  restoreUser(): void {
    const user = this.userToRestore;
    if (!user) return;
    this.isLoading = true;

    this.userService.restoreUser(user.id).subscribe({
      next: () => {
        this.isLoading = false;
        this.successMessage = `Usuario "${user.firstName} ${user.lastName}" restaurado: ya puede iniciar sesión`;
        this.cancelRestore();
        this.loadUsers();
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err.error?.message || "No se pudo restaurar el usuario";
        this.cancelRestore();
        this.loadUsers();
      }
    });
  }

  // --- Formateadores ---

  getUserInitials(user: User): string {
    const f = user.firstName?.charAt(0) || '';
    const l = user.lastName?.charAt(0) || '';
    return (f + l).toUpperCase();
  }

  formatDate(date: string | Date): string {
    if (!date) return 'N/A';
    return new Date(date).toLocaleDateString("es-PE", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    });
  }

  dismissSuccess(): void { this.successMessage = null; }
  dismissError(): void { this.errorMessage = null; }
}