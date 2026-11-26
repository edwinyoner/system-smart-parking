import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, exhaustMap, filter, merge } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { PaymentService } from '../../../../core/services/payment-service/payment.service';
import { PaymentTypeService } from '../../../../core/services/payment-service/payment-type.service';
import { Payment, PaymentFilters } from '../../../../core/models/payment-service/payment.model';
import { PaymentType } from '../../../../core/models/payment-service/payment-type.model';
import { ParkingPagedResponse } from '../../../../core/models/pagination.model';
import { AlertMessageComponent } from '../../../../shared/components/alert-message/alert-message.component';
import { liveRefresh } from '../../../../core/utils/live-refresh.util';
import {
  PAYMENT_CONCEPTS, PAYMENT_STATUSES, paymentConceptLabel, paymentSourceLink,
  paymentStatusBadge, paymentStatusIcon, paymentStatusLabel,
} from '../../../../core/utils/payment.util';

/** Historial de pagos de estadías y multas, el más reciente primero */
@Component({
  selector: 'app-payment-list',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, AlertMessageComponent],
  templateUrl: './payment-list.component.html',
  styleUrls: ['./payment-list.component.css']
})
export class PaymentListComponent implements OnInit {

  filterForm!: FormGroup;
  payments: Payment[] = [];
  paymentTypes: PaymentType[] = [];
  isLoading = false;
  errorMessage: string | null = null;

  currentPage = 1;
  pageSize = 10;
  totalItems = 0;
  totalPages = 0;
  pageSizeOptions = [5, 10, 15, 20, 25, 50];

  statuses = PAYMENT_STATUSES;
  concepts = PAYMENT_CONCEPTS;
  Math = Math;

  private destroyRef = inject(DestroyRef);
  private lastRequestId = 0;

  constructor(
    private fb: FormBuilder,
    private paymentService: PaymentService,
    private paymentTypeService: PaymentTypeService,
    private router: Router,
  ) {}

  ngOnInit(): void {
    this.filterForm = this.fb.group({
      search: [''],
      status: [''],
      concept: [''],
      paymentTypeId: [null],
      from: [''],
      to: [''],
    });
    // Incluye inactivos: un pago antiguo pudo hacerse con un tipo que ya no se ofrece
    this.paymentTypeService.getAll(0, 100).subscribe({
      next: page => (this.paymentTypes = page.content),
      error: () => (this.paymentTypes = []),
    });
    this.loadPayments();
    this.setupFilterListeners();
    this.startLiveRefresh();
  }

  /** Cada 5 s, sin spinner: un QR que se paga o un pago nuevo aparece sin recargar */
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

  private setupFilterListeners(): void {
    const search$ = this.filterForm.get('search')!.valueChanges.pipe(debounceTime(400), distinctUntilChanged());
    const others$ = merge(
      ...['status', 'concept', 'paymentTypeId', 'from', 'to'].map(name => this.filterForm.get(name)!.valueChanges)
    );
    merge(search$, others$).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.currentPage = 1;
      this.loadPayments();
    });
  }

  private filters(): PaymentFilters {
    // getRawValue: dentro de valueChanges, form.value aún trae el valor anterior
    const f = this.filterForm.getRawValue();
    return { search: f.search, status: f.status, concept: f.concept, paymentTypeId: f.paymentTypeId, from: f.from, to: f.to };
  }

  private request(silent: boolean) {
    return this.paymentService.getAll(this.currentPage - 1, this.pageSize, this.filters(), silent);
  }

  private showPage(response: ParkingPagedResponse<Payment>): void {
    this.payments = response.content;
    this.totalItems = response.totalElements;
    this.totalPages = response.totalPages;
    this.currentPage = response.pageNumber + 1;
    this.pageSize = response.pageSize;
  }

  loadPayments(): void {
    if (this.dateRangeInvalid) return;
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
        this.errorMessage = error.error?.message || 'Error al cargar los pagos';
      }
    });
  }

  get dateRangeInvalid(): boolean {
    const { from, to } = this.filterForm.getRawValue();
    return !!from && !!to && from > to;
  }

  clearFilters(): void {
    this.filterForm.reset({ search: '', status: '', concept: '', paymentTypeId: null, from: '', to: '' }, { emitEvent: false });
    this.currentPage = 1;
    this.loadPayments();
  }

  onPageSizeChange(event: Event): void {
    this.pageSize = Number((event.target as HTMLSelectElement).value);
    this.currentPage = 1;
    this.loadPayments();
  }

  goToPage(page: number): void {
    if (page >= 1 && page <= this.totalPages) {
      this.currentPage = page;
      this.loadPayments();
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

  viewPayment(id: number): void {
    this.router.navigate(['/payments', id]);
  }

  statusLabel = paymentStatusLabel;
  statusBadge = paymentStatusBadge;
  statusIcon = paymentStatusIcon;
  conceptLabel = paymentConceptLabel;
  sourceLink = paymentSourceLink;

  dismissError(): void { this.errorMessage = null; }
}
