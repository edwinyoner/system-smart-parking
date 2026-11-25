import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, exhaustMap, filter } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';

import { DocumentTypeService } from '../../../../core/services/parking-service/document-type.service';
import { DocumentType } from '../../../../core/models/parking-service/document-type.model';
// Importar ParkingPagedResponse (no PaginatedResponse)
import { ParkingPagedResponse } from '../../../../core/models/pagination.model';
import { AlertMessageComponent } from '../../../../shared/components/alert-message/alert-message.component';
import { takeSuccessMessage } from '../../../../core/utils/flash-message.util';
import { liveRefresh } from '../../../../core/utils/live-refresh.util';
import { AuthContextService } from '../../../../core/services/auth-service/auth-context.service';

@Component({
  selector: 'app-document-type-list',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, AlertMessageComponent],
  templateUrl: './document-type-list.component.html',
  styleUrls: ['./document-type-list.component.css']
})
export class DocumentTypeListComponent implements OnInit {

  filterForm!: FormGroup;
  documentTypes: DocumentType[] = [];
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
  typeToDelete: DocumentType | null = null;

  // Papelera: tipos eliminados que se pueden restaurar
  showDeleted = false;
  showRestoreConfirm = false;
  typeToRestore: DocumentType | null = null;

  Math = Math;

  private destroyRef = inject(DestroyRef);
  /** Identifica la última consulta: una respuesta vieja no pisa una página o filtro nuevos */
  private lastRequestId = 0;

  constructor(
    private fb: FormBuilder,
    private documentTypeService: DocumentTypeService,
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
    this.loadDocumentTypes();
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
    return this.documentTypeService.getAll(this.currentPage - 1, this.pageSize, search,
      this.showDeleted ? '' : status, this.showDeleted, silent);
  }

  private showPage(response: ParkingPagedResponse<DocumentType>): void {
    this.documentTypes      = response.content;
    this.totalItems  = response.totalElements;
    this.totalPages  = response.totalPages;
    this.currentPage = response.pageNumber + 1;  // base 0 → base 1
    this.pageSize    = response.pageSize;
    // Si la página quedó vacía (otro usuario eliminó o restauró), volver a la anterior
    if (this.documentTypes.length === 0 && this.currentPage > 1 && this.currentPage > this.totalPages) {
      this.currentPage = Math.max(this.totalPages, 1);
      this.loadDocumentTypes();
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
        this.loadDocumentTypes();
      });

    this.filterForm.get('status')?.valueChanges.subscribe(() => {
      this.currentPage = 1;
      this.loadDocumentTypes();
    });
  }

  loadDocumentTypes(): void {
    this.isLoading = true;

    const requestId = ++this.lastRequestId;

    this.request(false).subscribe({
      next: (response: ParkingPagedResponse<DocumentType>) => {
        if (requestId !== this.lastRequestId) return;
        this.isLoading = false;
        this.showPage(response);
      },
      error: (error) => {
        this.isLoading    = false;
        this.errorMessage = 'Error al cargar los tipos de documento';
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
    this.documentTypes = [];
    this.loadDocumentTypes();
  }

  confirmRestore(dt: DocumentType): void {
    this.typeToRestore = dt;
    this.showRestoreConfirm = true;
  }

  cancelRestore(): void {
    this.showRestoreConfirm = false;
    this.typeToRestore = null;
  }

  restoreDocumentType(): void {
    const dt = this.typeToRestore;
    if (!dt?.id) return;
    this.isLoading = true;

    this.documentTypeService.restore(dt.id).subscribe({
      next: () => {
        this.isLoading = false;
        this.successMessage = `Tipo de documento "${dt.code}" restaurado: ya está activo`;
        this.cancelRestore();
        this.loadDocumentTypes();
      },
      error: (error) => {
        this.isLoading = false;
        this.errorMessage = error.error?.message || 'No se pudo restaurar el tipo de documento';
        this.cancelRestore();
      }
    });
  }

  clearFilters(): void {
    this.filterForm.reset({ search: '', status: '' }, { emitEvent: false });
    this.currentPage = 1;
    this.loadDocumentTypes();
  }

  onPageSizeChange(event: any): void {
    this.pageSize = Number(event.target.value);
    this.currentPage = 1;
    this.loadDocumentTypes();
  }

  goToPage(page: number): void {
    if (page >= 1 && page <= this.totalPages) {
      this.currentPage = page;
      this.loadDocumentTypes();
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

  createDocumentType(): void { this.router.navigate(['/document-types/create']); }
  viewDocumentType(id: number): void { this.router.navigate(['/document-types', id]); }
  editDocumentType(id: number): void { this.router.navigate(['/document-types', id, 'edit']); }

  confirmDelete(dt: DocumentType): void {
    this.typeToDelete = dt;
    this.showDeleteConfirm = true;
  }

  cancelDelete(): void {
    this.showDeleteConfirm = false;
    this.typeToDelete = null;
  }

  deleteDocumentType(): void {
    if (!this.typeToDelete?.id) return;
    this.isLoading = true;

    this.documentTypeService.delete(this.typeToDelete.id).subscribe({
      next: () => {
        this.isLoading     = false;
        this.successMessage = `Tipo de documento "${this.typeToDelete?.code}" eliminado exitosamente`;
        this.showDeleteConfirm = false;
        this.typeToDelete = null;
        this.loadDocumentTypes();
      },
      error: (error) => {
        this.isLoading     = false;
        this.errorMessage  = error.error?.message || 'No se pudo eliminar el tipo de documento';
        this.showDeleteConfirm = false;
      }
    });
  }

  /** Los botones se muestran según los permisos del rol activo (se actualizan en vivo) */
  hasPermission(permission: string): boolean {
    return this.authContext.hasPermission(permission);
  }

  /** Uno que usan clientes o transacciones no se puede eliminar (el backend responde 409) */
  isInUse(dt: DocumentType): boolean {
    return !!dt.inUse;
  }

  deleteBlockedReason(dt: DocumentType): string {
    return `No se puede eliminar: lo usan ${dt.customersCount} cliente(s) y ${dt.transactionsCount} transacción(es). Puedes desactivarlo.`;
  }


  dismissSuccess(): void { this.successMessage = null; }
  dismissError(): void   { this.errorMessage = null; }
}