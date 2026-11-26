import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { EMPTY, catchError, exhaustMap, filter } from 'rxjs';
import { liveRefresh } from '../../../../core/utils/live-refresh.util';
import { channelLabel, confirmationLabel } from '../../../../core/utils/payment-type.util';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { PaymentTypeService } from '../../../../core/services/payment-service/payment-type.service';
import { PaymentType } from '../../../../core/models/payment-service/payment-type.model';
import { AlertMessageComponent } from '../../../../shared/components/alert-message/alert-message.component';

@Component({
  selector: 'app-payment-type-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, AlertMessageComponent],
  templateUrl: './payment-type-detail.component.html',
  styleUrls: ['./payment-type-detail.component.css']
})
export class PaymentTypeDetailComponent implements OnInit {

  paymentTypeId!: number;
  paymentType: PaymentType | null = null;
  isLoading = true;
  errorMessage: string | null = null;

  confirmationLabel = confirmationLabel;
  channelLabel = channelLabel;

  private destroyRef = inject(DestroyRef);

  constructor(
    private paymentTypeService: PaymentTypeService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.paymentTypeId = Number(this.route.snapshot.paramMap.get('id'));
    this.loadPaymentType();
    this.startLiveRefresh();
  }

  /** Mantiene el detalle al día (cambios de otro usuario) sin spinner ni recargar */
  private startLiveRefresh(): void {
    liveRefresh(5000).pipe(
      filter(() => !this.isLoading && !!this.paymentType),
      exhaustMap(() => this.paymentTypeService.getById(this.paymentTypeId, true).pipe(
        catchError((error: HttpErrorResponse) => {
          if (error.status === 404) {
            this.paymentType = null;
            this.errorMessage = 'Este tipo de pago fue eliminado. Puedes restaurarlo desde Eliminados.';
          }
          return EMPTY;
        })
      )),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(dt => this.paymentType = dt);
  }

  private loadPaymentType(): void {
    this.isLoading = true;
    this.paymentTypeService.getById(this.paymentTypeId).subscribe({
      next: (dt: PaymentType) => { this.paymentType = dt; this.isLoading = false; },
      error: (err) => {
        this.isLoading    = false;
        this.errorMessage = 'Error al cargar los detalles del tipo de pago';
        console.error(err);
      }
    });
  }

  goBack(): void { this.router.navigate(['/payment-types']); }
  dismissError(): void { this.errorMessage = null; }
}