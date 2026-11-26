import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { EMPTY, catchError, exhaustMap, filter } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';

import { ParkingService } from '../../../../core/services/parking-service/parking.service';
import { Parking } from '../../../../core/models/parking-service/parking.model';
import { ParkingPagedResponse } from '../../../../core/models/pagination.model';
import { AlertMessageComponent } from '../../../../shared/components/alert-message/alert-message.component';
import { takeSuccessMessage } from '../../../../core/utils/flash-message.util';
import { liveRefresh } from '../../../../core/utils/live-refresh.util';
import { LOCATION_STATUS_OPTIONS, apiError, locationStatus } from '../../../../core/utils/parking.util';
import { AuthContextService } from '../../../../core/services/auth-service/auth-context.service';

/**
 * Lista de parqueos con su capacidad calculada (se actualiza en vivo cada 5 s),
 * filtros, papelera y acciones según los permisos del rol activo.
 */
@Component({
  selector: 'app-parking-list',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, AlertMessageComponent],
  templateUrl: './parking-list.component.html',
  styleUrls: ['./parking-list.component.css']
})
export class ParkingListComponent implements OnInit {

  filterForm!: FormGroup;
  parkings: Parking[] = [];
  isLoading = false;
  successMessage: string | null = null;
  errorMessage: string | null = null;

  currentPage = 1;
  pageSize = 10;
  totalItems = 0;
  totalPages = 0;
  pageSizeOptions = [5, 10, 15, 20, 25, 50];

  showDeleteConfirm = false;
  parkingToDelete: Parking | null = null;

  showDeleted = false;
  showRestoreConfirm = false;
  parkingToRestore: Parking | null = null;

  readonly statusOptions = LOCATION_STATUS_OPTIONS;
  readonly status = locationStatus;
  Math = Math;

  private destroyRef = inject(DestroyRef);
  /** Identifica la última consulta: una respuesta vieja no pisa una página o filtro nuevos */
  private lastRequestId = 0;

  constructor(
    private fb: FormBuilder,
    private parkingService: ParkingService,
    private router: Router,
    private authContext: AuthContextService
  ) {
    this.successMessage = takeSuccessMessage();
    if (this.successMessage) {
      setTimeout(() => (this.successMessage = null), 5000);
    }
  }

  ngOnInit(): void {
    this.filterForm = this.fb.group({ search: [''], status: [''] });
    this.loadParkings();
    this.filterForm.get('search')?.valueChanges
      .pipe(debounceTime(400), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => { this.currentPage = 1; this.loadParkings(); });
    this.filterForm.get('status')?.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => { this.currentPage = 1; this.loadParkings(); });
    this.startLiveRefresh();
  }

  /** Cada 5 s (y al volver a la ventana) recarga la página sin spinner: ocupación en vivo */
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
    return this.parkingService.getAll(this.currentPage - 1, this.pageSize, search,
      this.showDeleted ? '' : status, this.showDeleted, silent);
  }

  private showPage(response: ParkingPagedResponse<Parking>): void {
    this.parkings    = response.content;
    this.totalItems  = response.totalElements;
    this.totalPages  = response.totalPages;
    this.currentPage = response.pageNumber + 1;
    this.pageSize    = response.pageSize;
    if (this.parkings.length === 0 && this.currentPage > 1 && this.currentPage > this.totalPages) {
      this.currentPage = Math.max(this.totalPages, 1);
      this.loadParkings();
    }
  }

  loadParkings(): void {
    this.isLoading = true;
    const requestId = ++this.lastRequestId;
    this.request(false).subscribe({
      next: response => {
        if (requestId !== this.lastRequestId) return;
        this.isLoading = false;
        this.showPage(response);
      },
      error: error => {
        this.isLoading = false;
        this.errorMessage = apiError(error, 'Error al cargar los parqueos');
      }
    });
  }

  setShowDeleted(deleted: boolean): void {
    if (this.showDeleted === deleted) return;
    this.showDeleted = deleted;
    const status = this.filterForm.get('status');
    deleted ? status?.disable({ emitEvent: false }) : status?.enable({ emitEvent: false });
    this.currentPage = 1;
    this.parkings = [];
    this.loadParkings();
  }

  clearFilters(): void {
    this.filterForm.reset({ search: '', status: '' }, { emitEvent: false });
    this.currentPage = 1;
    this.loadParkings();
  }

  onPageSizeChange(event: Event): void {
    this.pageSize = Number((event.target as HTMLSelectElement).value);
    this.currentPage = 1;
    this.loadParkings();
  }

  goToPage(page: number): void {
    if (page >= 1 && page <= this.totalPages) {
      this.currentPage = page;
      this.loadParkings();
    }
  }

  getPages(): number[] {
    const pages = [];
    const max = 5;
    let start = Math.max(1, this.currentPage - Math.floor(max / 2));
    const end = Math.min(this.totalPages, start + max - 1);
    if (end - start + 1 < max) start = Math.max(1, end - max + 1);
    for (let i = start; i <= end; i++) pages.push(i);
    return pages;
  }

  createParking(): void { this.router.navigate(['/parkings/create']); }
  viewParking(id: number): void { this.router.navigate(['/parkings', id]); }
  editParking(id: number): void { this.router.navigate(['/parkings', id, 'edit']); }

  // ========================= ELIMINAR Y RESTAURAR =========================

  confirmDelete(parking: Parking): void {
    this.parkingToDelete = parking;
    this.showDeleteConfirm = true;
  }

  cancelDelete(): void {
    this.showDeleteConfirm = false;
    this.parkingToDelete = null;
  }

  deleteParking(): void {
    const parking = this.parkingToDelete;
    if (!parking?.id) return;
    this.isLoading = true;
    this.parkingService.delete(parking.id).subscribe({
      next: () => {
        this.isLoading = false;
        this.successMessage = `Parqueo "${parking.name}" eliminado. Puedes restaurarlo desde Eliminados.`;
        this.cancelDelete();
        this.loadParkings();
      },
      error: error => {
        this.isLoading = false;
        this.errorMessage = apiError(error, 'No se pudo eliminar el parqueo');
        this.cancelDelete();
      }
    });
  }

  confirmRestore(parking: Parking): void {
    this.parkingToRestore = parking;
    this.showRestoreConfirm = true;
  }

  cancelRestore(): void {
    this.showRestoreConfirm = false;
    this.parkingToRestore = null;
  }

  restoreParking(): void {
    const parking = this.parkingToRestore;
    if (!parking?.id) return;
    this.isLoading = true;
    this.parkingService.restore(parking.id).subscribe({
      next: () => {
        this.isLoading = false;
        this.successMessage = `Parqueo "${parking.name}" restaurado con sus zonas, espacios y horario.`;
        this.cancelRestore();
        this.loadParkings();
      },
      error: error => {
        this.isLoading = false;
        this.errorMessage = apiError(error, 'No se pudo restaurar el parqueo');
        this.cancelRestore();
      }
    });
  }

  // ========================= VISTA =========================

  hasPermission(permission: string): boolean {
    return this.authContext.hasPermission(permission);
  }

  /** Un parqueo con vehículos dentro no se elimina (409) */
  hasVehiclesInside(p: Parking): boolean {
    return (p.occupiedSpaces ?? 0) > 0;
  }

  occupancyClass(p: Parking): string {
    const pct = p.occupancyPercentage ?? 0;
    if (pct >= 90) return 'bg-danger';
    if (pct >= 70) return 'bg-warning';
    return 'bg-success';
  }

  dismissSuccess(): void { this.successMessage = null; }
  dismissError(): void   { this.errorMessage = null; }
}
