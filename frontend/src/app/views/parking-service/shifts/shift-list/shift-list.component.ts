import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, exhaustMap, filter } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';

import { ShiftService } from '../../../../core/services/parking-service/shift.service';
import { Shift } from '../../../../core/models/parking-service/shift.model';
// Importar ParkingPagedResponse (no PaginatedResponse)
import { ParkingPagedResponse } from '../../../../core/models/pagination.model';
import { AlertMessageComponent } from '../../../../shared/components/alert-message/alert-message.component';
import { takeSuccessMessage } from '../../../../core/utils/flash-message.util';
import { formatShiftDuration } from '../../../../core/utils/shift.util';
import { liveRefresh } from '../../../../core/utils/live-refresh.util';
import { AuthContextService } from '../../../../core/services/auth-service/auth-context.service';

@Component({
  selector: 'app-shift-list',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, AlertMessageComponent],
  templateUrl: './shift-list.component.html',
  styleUrls: ['./shift-list.component.css']
})
export class ShiftListComponent implements OnInit {

  filterForm!: FormGroup;
  shifts: Shift[] = [];
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
  shiftToDelete: Shift | null = null;

  // Papelera: turnos eliminados que se pueden restaurar
  showDeleted = false;
  showRestoreConfirm = false;
  shiftToRestore: Shift | null = null;

  Math = Math;

  private destroyRef = inject(DestroyRef);
  /** Identifica la última consulta: una respuesta vieja no pisa una página o filtro nuevos */
  private lastRequestId = 0;

  constructor(
    private fb: FormBuilder,
    private shiftService: ShiftService,
    private router: Router,
    private authContext: AuthContextService
  ) {
    this.successMessage = takeSuccessMessage();
    if (this.successMessage) {
      setTimeout(() => (this.successMessage = null), 5000);
    }
  }

  ngOnInit(): void {
    this.initFilterForm();
    this.loadShifts();
    this.setupFilterListeners();
    this.startLiveRefresh();
  }

  /**
   * Cada 5 s (y al volver a la ventana) recarga la página actual sin spinner:
   * lo que otro usuario cree, edite, elimine o restaure aparece sin refrescar.
   */
  private startLiveRefresh(): void {
    liveRefresh(5000).pipe(
      filter(() => !this.isLoading),
      exhaustMap(() => {
        const requestId = this.lastRequestId;
        return this.request(true).pipe(
          filter(() => requestId === this.lastRequestId),
          catchError(() => EMPTY)
        );
      }),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(response => this.showPage(response));
  }

  private request(silent: boolean) {
    const search = this.filterForm.get('search')?.value ?? '';
    const status = this.filterForm.get('status')?.value ?? '';
    return this.shiftService.getAll(this.currentPage - 1, this.pageSize, search,
      this.showDeleted ? '' : status, this.showDeleted, silent);
  }

  private showPage(response: ParkingPagedResponse<Shift>): void {
    this.shifts      = response.content;
    this.totalItems  = response.totalElements;
    this.totalPages  = response.totalPages;
    this.currentPage = response.pageNumber + 1;  // base 0 → base 1
    this.pageSize    = response.pageSize;
    // Si la página quedó vacía (otro usuario eliminó o restauró), volver a la anterior
    if (this.shifts.length === 0 && this.currentPage > 1 && this.currentPage > this.totalPages) {
      this.currentPage = Math.max(this.totalPages, 1);
      this.loadShifts();
    }
  }

  private initFilterForm(): void {
    this.filterForm = this.fb.group({
      search: [''],
      status: ['']
    });
  }

  private setupFilterListeners(): void {
    this.filterForm.get('search')?.valueChanges
      .pipe(debounceTime(400), distinctUntilChanged())
      .subscribe(() => {
        this.currentPage = 1;
        this.loadShifts();
      });

    this.filterForm.get('status')?.valueChanges.subscribe(() => {
      this.currentPage = 1;
      this.loadShifts();
    });
  }

  loadShifts(): void {
    this.isLoading = true;

    const requestId = ++this.lastRequestId;

    this.request(false).subscribe({
      next: (response: ParkingPagedResponse<Shift>) => {
        if (requestId !== this.lastRequestId) return;
        this.isLoading = false;
        this.showPage(response);
      },
      error: (error) => {
        this.isLoading    = false;
        this.errorMessage = 'Error al cargar los turnos del sistema';
        console.error(error);
      }
    });
  }

  /** Cambia entre turnos vigentes y la papelera. En la papelera no se filtra por estado */
  setShowDeleted(deleted: boolean): void {
    if (this.showDeleted === deleted) return;
    this.showDeleted = deleted;
    const status = this.filterForm.get('status');
    deleted ? status?.disable({ emitEvent: false }) : status?.enable({ emitEvent: false });
    this.currentPage = 1;
    this.shifts = [];
    this.loadShifts();
  }

  confirmRestore(shift: Shift): void {
    this.shiftToRestore = shift;
    this.showRestoreConfirm = true;
  }

  cancelRestore(): void {
    this.showRestoreConfirm = false;
    this.shiftToRestore = null;
  }

  restoreShift(): void {
    const shift = this.shiftToRestore;
    if (!shift?.id) return;
    this.isLoading = true;

    this.shiftService.restore(shift.id).subscribe({
      next: () => {
        this.isLoading = false;
        this.successMessage = `Turno "${shift.name}" restaurado: ya está activo y se puede usar en los parqueos`;
        this.cancelRestore();
        this.loadShifts();
      },
      error: (error) => {
        this.isLoading = false;
        this.errorMessage = error.error?.message || 'No se pudo restaurar el turno';
        this.cancelRestore();
      }
    });
  }

  clearFilters(): void {
    this.filterForm.reset({ search: '', status: '' }, { emitEvent: false });
    this.currentPage = 1;
    this.loadShifts();
  }

  onPageSizeChange(event: any): void {
    this.pageSize = Number(event.target.value);
    this.currentPage = 1;
    this.loadShifts();
  }

  goToPage(page: number): void {
    if (page >= 1 && page <= this.totalPages) {
      this.currentPage = page;
      this.loadShifts();
    }
  }

  getPages(): number[] {
    const pages = [];
    const max = 5;
    let start = Math.max(1, this.currentPage - Math.floor(max / 2));
    let end   = Math.min(this.totalPages, start + max - 1);
    if (end - start + 1 < max) start = Math.max(1, end - max + 1);
    for (let i = start; i <= end; i++) pages.push(i);
    return pages;
  }

  createShift(): void { this.router.navigate(['/shifts/create']); }
  viewShift(id: number): void { this.router.navigate(['/shifts', id]); }
  editShift(id: number): void { this.router.navigate(['/shifts', id, 'edit']); }

  confirmDelete(shift: Shift): void {
    this.shiftToDelete = shift;
    this.showDeleteConfirm = true;
  }

  cancelDelete(): void {
    this.showDeleteConfirm = false;
    this.shiftToDelete = null;
  }

  deleteShift(): void {
    if (!this.shiftToDelete?.id) return;
    this.isLoading = true;

    this.shiftService.delete(this.shiftToDelete.id).subscribe({
      next: () => {
        this.isLoading     = false;
        this.successMessage = `Turno "${this.shiftToDelete?.name}" eliminado exitosamente`;
        this.showDeleteConfirm = false;
        this.shiftToDelete = null;
        this.loadShifts();
      },
      error: (error) => {
        this.isLoading     = false;
        this.errorMessage  = error.error?.message || 'No se pudo eliminar el turno';
        this.showDeleteConfirm = false;
      }
    });
  }

  /** Los botones se muestran según los permisos del rol activo (se actualizan en vivo) */
  hasPermission(permission: string): boolean {
    return this.authContext.hasPermission(permission);
  }

  /** Un turno que usa algún parqueo no se puede eliminar (el backend responde 409) */
  isInUse(shift: Shift): boolean {
    return (shift.usedByParkings?.length ?? 0) > 0;
  }

  deleteBlockedReason(shift: Shift): string {
    return `No se puede eliminar: lo usa ${shift.usedByParkings?.join(', ')}. Puedes desactivarlo.`;
  }

  formatDuration(shift: Shift): string {
    return formatShiftDuration(shift.durationHours);
  }

  formatTime(time: string): string {
    if (!time) return 'N/A';
    return time.substring(0, 5);
  }

  dismissSuccess(): void { this.successMessage = null; }
  dismissError(): void   { this.errorMessage = null; }
}