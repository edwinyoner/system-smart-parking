import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, exhaustMap, filter } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';

import { PaymentTypeService } from '../../../../core/services/payment-service/payment-type.service';
import { PaymentType } from '../../../../core/models/payment-service/payment-type.model';
// Importar ParkingPagedResponse (no PaginatedResponse)
import { ParkingPagedResponse } from '../../../../core/models/pagination.model';
import { AlertMessageComponent } from '../../../../shared/components/alert-message/alert-message.component';
import { channelLabel, confirmationLabel } from '../../../../core/utils/payment-type.util';
import { takeSuccessMessage } from '../../../../core/utils/flash-message.util';
import { liveRefresh } from '../../../../core/utils/live-refresh.util';
import { AuthContextService } from '../../../../core/services/auth-service/auth-context.service';

@Component({
  selector: 'app-payment-type-list',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, AlertMessageComponent],
  templateUrl: './payment-type-list.component.html',
  styleUrls: ['./payment-type-list.component.css']
})
export class PaymentTypeListComponent implements OnInit {

  filterForm!: FormGroup;
  paymentTypes: PaymentType[] = [];
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
  typeToDelete: PaymentType | null = null;

  // Papelera: tipos eliminados que se pueden restaurar
  showDeleted = false;
  showRestoreConfirm = false;
  typeToRestore: PaymentType | null = null;

  Math = Math;

  private destroyRef = inject(DestroyRef);
  /** Identifica la última consulta: una respuesta vieja no pisa una página o filtro nuevos */
  private lastRequestId = 0;

  constructor(
    private fb: FormBuilder,
    private paymentTypeService: PaymentTypeService,
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
    this.loadPaymentTypes();
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
    return this.paymentTypeService.getAll(this.currentPage - 1, this.pageSize, search,
      this.showDeleted ? '' : status, this.showDeleted, silent);
  }

  private showPage(response: ParkingPagedResponse<PaymentType>): void {
    this.paymentTypes      = response.content;
    this.totalItems  = response.totalElements;
    this.totalPages  = response.totalPages;
    this.currentPage = response.pageNumber + 1;  // base 0 → base 1
    this.pageSize    = response.pageSize;
    // Si la página quedó vacía (otro usuario eliminó o restauró), volver a la anterior
    if (this.paymentTypes.length === 0 && this.currentPage > 1 && this.currentPage > this.totalPages) {
      this.currentPage = Math.max(this.totalPages, 1);
      this.loadPaymentTypes();
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
        this.loadPaymentTypes();
      });

    this.filterForm.get('status')?.valueChanges.subscribe(() => {
      this.currentPage = 1;
      this.loadPaymentTypes();
    });
  }

  loadPaymentTypes(): void {
    this.isLoading = true;

    const requestId = ++this.lastRequestId;

    this.request(false).subscribe({
      next: (response: ParkingPagedResponse<PaymentType>) => {
        if (requestId !== this.lastRequestId) return;
        this.isLoading = false;
        this.showPage(response);
      },
      error: (error) => {
        this.isLoading    = false;
        this.errorMessage = 'Error al cargar los tipos de pago';
        console.error(error);
      }
    });
  }

  /** Cambia entre tipos vigentes y la papelera. En la papelera no se filtra por estado */
  setShowDeleted(deleted: boolean): void {
    if (this.showDeleted === deleted) return;
    this.showDeleted = deleted;
    const status = this.filterForm.get('status');
    deleted ? status?.disable({ emitEvent: false }) : status?.enable({ emitEvent: false });
    this.currentPage = 1;
    this.paymentTypes = [];
    this.loadPaymentTypes();
  }

  confirmRestore(dt: PaymentType): void {
    this.typeToRestore = dt;
    this.showRestoreConfirm = true;
  }

  cancelRestore(): void {
    this.showRestoreConfirm = false;
    this.typeToRestore = null;
  }

  restorePaymentType(): void {
    const dt = this.typeToRestore;
    if (!dt?.id) return;
    this.isLoading = true;

    this.paymentTypeService.restore(dt.id).subscribe({
      next: () => {
        this.isLoading = false;
        this.successMessage = `Tipo de pago "${dt.code}" restaurado: ya está activo`;
        this.cancelRestore();
        this.loadPaymentTypes();
      },
      error: (error) => {
        this.isLoading = false;
        this.errorMessage = error.error?.message || 'No se pudo restaurar el tipo de pago';
        this.cancelRestore();
      }
    });
  }

  clearFilters(): void {
    this.filterForm.reset({ search: '', status: '' }, { emitEvent: false });
    this.currentPage = 1;
    this.loadPaymentTypes();
  }

  onPageSizeChange(event: any): void {
    this.pageSize = Number(event.target.value);
    this.currentPage = 1;
    this.loadPaymentTypes();
  }

  goToPage(page: number): void {
    if (page >= 1 && page <= this.totalPages) {
      this.currentPage = page;
      this.loadPaymentTypes();
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

  createPaymentType(): void { this.router.navigate(['/payment-types/create']); }
  viewPaymentType(id: number): void { this.router.navigate(['/payment-types', id]); }
  editPaymentType(id: number): void { this.router.navigate(['/payment-types', id, 'edit']); }

  confirmDelete(dt: PaymentType): void {
    this.typeToDelete = dt;
    this.showDeleteConfirm = true;
  }

  cancelDelete(): void {
    this.showDeleteConfirm = false;
    this.typeToDelete = null;
  }

  deletePaymentType(): void {
    if (!this.typeToDelete?.id) return;
    this.isLoading = true;

    this.paymentTypeService.delete(this.typeToDelete.id).subscribe({
      next: () => {
        this.isLoading     = false;
        this.successMessage = `Tipo de pago "${this.typeToDelete?.code}" eliminado exitosamente`;
        this.showDeleteConfirm = false;
        this.typeToDelete = null;
        this.loadPaymentTypes();
      },
      error: (error) => {
        this.isLoading     = false;
        this.errorMessage  = error.error?.message || 'No se pudo eliminar el tipo de pago';
        this.showDeleteConfirm = false;
      }
    });
  }

  /** Los botones se muestran según los permisos del rol activo (se actualizan en vivo) */
  hasPermission(permission: string): boolean {
    return this.authContext.hasPermission(permission);
  }

  confirmationLabel = confirmationLabel;

  channelsLabel(dt: PaymentType): string {
    return dt.channels.map(channelLabel).join(', ');
  }


  dismissSuccess(): void { this.successMessage = null; }
  dismissError(): void   { this.errorMessage = null; }
}