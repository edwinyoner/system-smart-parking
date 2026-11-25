import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, exhaustMap, filter } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';

import { RateService } from '../../../../core/services/parking-service/rate.service';
import { Rate } from '../../../../core/models/parking-service/rate.model';
// Importar ParkingPagedResponse (no PaginatedResponse)
import { ParkingPagedResponse } from '../../../../core/models/pagination.model';
import { AlertMessageComponent } from '../../../../shared/components/alert-message/alert-message.component';
import { takeSuccessMessage } from '../../../../core/utils/flash-message.util';
import { describeBilling } from '../../../../core/utils/rate.util';
import { liveRefresh } from '../../../../core/utils/live-refresh.util';
import { AuthContextService } from '../../../../core/services/auth-service/auth-context.service';

@Component({
  selector: 'app-rate-list',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, AlertMessageComponent],
  templateUrl: './rate-list.component.html',
  styleUrls: ['./rate-list.component.css']
})
export class RateListComponent implements OnInit {

  filterForm!: FormGroup;
  rates: Rate[] = [];
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
  rateToDelete: Rate | null = null;

  // Papelera: tarifas eliminadas que se pueden restaurar
  showDeleted = false;
  showRestoreConfirm = false;
  rateToRestore: Rate | null = null;

  Math = Math;

  private destroyRef = inject(DestroyRef);
  /** Identifica la última consulta: una respuesta vieja no pisa una página o filtro nuevos */
  private lastRequestId = 0;

  constructor(
    private fb: FormBuilder,
    private rateService: RateService,
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
    this.loadRates();
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
    return this.rateService.getAll(this.currentPage - 1, this.pageSize, search,
      this.showDeleted ? '' : status, this.showDeleted, silent);
  }

  private showPage(response: ParkingPagedResponse<Rate>): void {
    this.rates      = response.content;
    this.totalItems  = response.totalElements;
    this.totalPages  = response.totalPages;
    this.currentPage = response.pageNumber + 1;  // base 0 → base 1
    this.pageSize    = response.pageSize;
    // Si la página quedó vacía (otro usuario eliminó o restauró), volver a la anterior
    if (this.rates.length === 0 && this.currentPage > 1 && this.currentPage > this.totalPages) {
      this.currentPage = Math.max(this.totalPages, 1);
      this.loadRates();
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
        this.loadRates();
      });

    this.filterForm.get('status')?.valueChanges.subscribe(() => {
      this.currentPage = 1;
      this.loadRates();
    });
  }

  loadRates(): void {
    this.isLoading = true;

    const requestId = ++this.lastRequestId;

    this.request(false).subscribe({
      next: (response: ParkingPagedResponse<Rate>) => {
        if (requestId !== this.lastRequestId) return;
        this.isLoading = false;
        this.showPage(response);
      },
      error: (error) => {
        this.isLoading    = false;
        this.errorMessage = 'Error al cargar las tarifas del sistema';
        console.error(error);
      }
    });
  }

  /** Cambia entre tarifas vigentes y la papelera. En la papelera no se filtra por estado */
  setShowDeleted(deleted: boolean): void {
    if (this.showDeleted === deleted) return;
    this.showDeleted = deleted;
    const status = this.filterForm.get('status');
    deleted ? status?.disable({ emitEvent: false }) : status?.enable({ emitEvent: false });
    this.currentPage = 1;
    this.rates = [];
    this.loadRates();
  }

  confirmRestore(rate: Rate): void {
    this.rateToRestore = rate;
    this.showRestoreConfirm = true;
  }

  cancelRestore(): void {
    this.showRestoreConfirm = false;
    this.rateToRestore = null;
  }

  restoreRate(): void {
    const rate = this.rateToRestore;
    if (!rate?.id) return;
    this.isLoading = true;

    this.rateService.restore(rate.id).subscribe({
      next: () => {
        this.isLoading = false;
        this.successMessage = `Tarifa "${rate.name}" restaurada: ya está activa y se puede asignar a los parqueos`;
        this.cancelRestore();
        this.loadRates();
      },
      error: (error) => {
        this.isLoading = false;
        this.errorMessage = error.error?.message || 'No se pudo restaurar la tarifa';
        this.cancelRestore();
      }
    });
  }

  clearFilters(): void {
    this.filterForm.reset({ search: '', status: '' }, { emitEvent: false });
    this.currentPage = 1;
    this.loadRates();
  }

  onPageSizeChange(event: any): void {
    this.pageSize = Number(event.target.value);
    this.currentPage = 1;
    this.loadRates();
  }

  goToPage(page: number): void {
    if (page >= 1 && page <= this.totalPages) {
      this.currentPage = page;
      this.loadRates();
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

  createRate(): void { this.router.navigate(['/rates/create']); }
  viewRate(id: number): void { this.router.navigate(['/rates', id]); }
  editRate(id: number): void { this.router.navigate(['/rates', id, 'edit']); }

  confirmDelete(rate: Rate): void {
    this.rateToDelete = rate;
    this.showDeleteConfirm = true;
  }

  cancelDelete(): void {
    this.showDeleteConfirm = false;
    this.rateToDelete = null;
  }

  deleteRate(): void {
    if (!this.rateToDelete?.id) return;
    this.isLoading = true;

    this.rateService.delete(this.rateToDelete.id).subscribe({
      next: () => {
        this.isLoading     = false;
        this.successMessage = `Tarifa "${this.rateToDelete?.name}" eliminada exitosamente`;
        this.showDeleteConfirm = false;
        this.rateToDelete = null;
        this.loadRates();
      },
      error: (error) => {
        this.isLoading     = false;
        this.errorMessage  = error.error?.message || 'No se pudo eliminar la tarifa';
        this.showDeleteConfirm = false;
      }
    });
  }

  /** Los botones se muestran según los permisos del rol activo (se actualizan en vivo) */
  hasPermission(permission: string): boolean {
    return this.authContext.hasPermission(permission);
  }

  /** Una tarifa que usa algún parqueo no se puede eliminar (el backend responde 409) */
  isInUse(rate: Rate): boolean {
    return (rate.usedByParkings?.length ?? 0) > 0;
  }

  deleteBlockedReason(rate: Rate): string {
    return `No se puede eliminar: la usa ${rate.usedByParkings?.join(', ')}. Puedes desactivarla.`;
  }

  billingLabel(rate: Rate): string {
    return describeBilling(rate);
  }

  dismissSuccess(): void { this.successMessage = null; }
  dismissError(): void   { this.errorMessage = null; }
}