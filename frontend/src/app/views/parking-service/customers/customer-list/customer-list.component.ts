// src/app/views/parking-service/customers/customer-list/customer-list.component.ts
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Subject, catchError, debounceTime, exhaustMap, merge, switchMap } from 'rxjs';
import {
  AlertComponent, BadgeComponent, ButtonDirective, CardBodyComponent, CardComponent, CardHeaderComponent, SpinnerComponent
} from '@coreui/angular';

import { CustomerService } from '../../../../core/services/parking-service/customer.service';
import { Customer } from '../../../../core/models/parking-service/customer.model';
import { ParkingPagedResponse } from '../../../../core/models/pagination.model';
import { liveRefresh } from '../../../../core/utils/live-refresh.util';
import { apiError } from '../../../../core/utils/parking.util';

const PAGE_SIZE = 20;

/**
 * Clientes: los conductores que entraron con documento (se registran solos en la entrada).
 * Búsqueda por nombre, documento, celular o correo; en vivo cada 10 s.
 */
@Component({
  selector: 'app-customer-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, CardComponent, CardHeaderComponent, CardBodyComponent,
    ButtonDirective, SpinnerComponent, AlertComponent, BadgeComponent],
  templateUrl: './customer-list.component.html',
  styleUrls: ['./customer-list.component.css']
})
export class CustomerListComponent implements OnInit {

  private readonly customerService = inject(CustomerService);
  private readonly destroyRef = inject(DestroyRef);

  search = '';
  page = 0;
  result: ParkingPagedResponse<Customer> | null = null;
  loading = true;
  errorMessage = '';
  lastUpdate: Date | null = null;

  private readonly reload$ = new Subject<boolean>();
  private readonly search$ = new Subject<void>();

  ngOnInit(): void {
    this.search$.pipe(debounceTime(350), takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.page = 0;
      this.reload$.next(false);
    });
    merge(
      this.reload$.pipe(switchMap(silent => this.fetch(silent))),
      liveRefresh(10000).pipe(exhaustMap(() => this.fetch(true)))
    ).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(r => {
      this.result = r;
      this.loading = false;
      this.lastUpdate = new Date();
    });
    this.reload$.next(false);
  }

  private fetch(silent: boolean) {
    return this.customerService.list(this.page, PAGE_SIZE, this.search, silent).pipe(catchError(err => {
      this.loading = false;
      if (!silent) this.errorMessage = apiError(err, 'No se pudieron cargar los clientes');
      return EMPTY;
    }));
  }

  onSearch(): void {
    this.search$.next();
  }

  goTo(page: number): void {
    if (!this.result || page < 0 || page >= this.result.totalPages) return;
    this.page = page;
    this.reload$.next(false);
  }

  get pages(): number[] {
    const total = this.result?.totalPages ?? 0;
    const from = Math.max(0, Math.min(this.page - 2, total - 5));
    return Array.from({ length: Math.min(5, total) }, (_, i) => from + i);
  }
}
