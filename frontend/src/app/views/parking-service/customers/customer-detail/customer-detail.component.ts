// src/app/views/parking-service/customers/customer-detail/customer-detail.component.ts
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, exhaustMap, filter } from 'rxjs';
import {
  AlertComponent, ButtonDirective, CardBodyComponent, CardComponent, CardHeaderComponent, SpinnerComponent
} from '@coreui/angular';

import { CustomerService } from '../../../../core/services/parking-service/customer.service';
import { PermissionService } from '../../../../core/services/auth-service/permission.service';
import { CustomerDetail } from '../../../../core/models/parking-service/customer.model';
import { liveRefresh } from '../../../../core/utils/live-refresh.util';
import { apiError } from '../../../../core/utils/parking.util';
import { PHONE_ERROR_MESSAGE, PHONE_MAX_LENGTH, PHONE_PATTERN, keepPhoneDigits } from '../../../../core/utils/phone.util';
import { HistoryPanelComponent } from '../../shared/history-panel/history-panel.component';

/**
 * Cliente: datos, contacto editable (customers.update), vehículos que usó, totales e historial
 * de estadías e infracciones. En vivo cada 10 s mientras no se edita.
 */
@Component({
  selector: 'app-customer-detail',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, CardComponent, CardHeaderComponent, CardBodyComponent,
    ButtonDirective, SpinnerComponent, AlertComponent, HistoryPanelComponent],
  templateUrl: './customer-detail.component.html',
  styleUrls: ['./customer-detail.component.css']
})
export class CustomerDetailComponent implements OnInit {

  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);
  private readonly customerService = inject(CustomerService);
  private readonly permissions = inject(PermissionService);
  private readonly destroyRef = inject(DestroyRef);

  readonly canEdit = this.permissions.hasPermission('customers.update');
  readonly canSeeInfractions = this.permissions.hasPermission('infractions.read');
  readonly canSeeVehicles = this.permissions.hasPermission('vehicles.read');
  readonly phoneMax = PHONE_MAX_LENGTH;
  readonly phoneError = PHONE_ERROR_MESSAGE;
  readonly keepPhoneDigits = keepPhoneDigits;

  id = 0;
  detail: CustomerDetail | null = null;
  loading = true;
  errorMessage = '';
  successMessage = '';
  editing = false;
  saving = false;

  readonly form = this.fb.group({
    phone: this.fb.control('', Validators.pattern(PHONE_PATTERN)),
    email: this.fb.control('', [Validators.email, Validators.maxLength(100)]),
    address: this.fb.control('', Validators.maxLength(200))
  });

  readonly loadStays = (page: number, silent: boolean) => this.customerService.getStays(this.id, page, 10, silent);
  readonly loadInfractions = (page: number, silent: boolean) => this.customerService.getInfractions(this.id, page, 10, silent);

  ngOnInit(): void {
    this.id = Number(this.route.snapshot.paramMap.get('id'));
    this.customerService.getById(this.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: d => {
        this.detail = d;
        this.loading = false;
      },
      error: err => {
        this.errorMessage = apiError(err, 'No se pudo cargar el cliente');
        this.loading = false;
      }
    });

    liveRefresh(10000).pipe(
      filter(() => !!this.detail && !this.editing),
      exhaustMap(() => this.customerService.getById(this.id, true).pipe(catchError(() => EMPTY))),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(d => (this.detail = d));
  }

  startEdit(): void {
    const c = this.detail!.customer;
    this.form.reset({ phone: c.phone ?? '', email: c.email ?? '', address: c.address ?? '' });
    this.editing = true;
  }

  invalid(name: 'phone' | 'email' | 'address'): boolean {
    const c = this.form.controls[name];
    return c.invalid && (c.dirty || c.touched);
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving = true;
    this.customerService.updateContact(this.id, {
      phone: v.phone?.trim() || null, email: v.email?.trim() || null, address: v.address?.trim() || null
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: customer => {
        this.saving = false;
        this.editing = false;
        this.detail = { ...this.detail!, customer };
        this.successMessage = 'Datos de contacto guardados';
      },
      error: err => {
        this.saving = false;
        this.errorMessage = apiError(err, 'No se pudieron guardar los datos de contacto');
      }
    });
  }
}
