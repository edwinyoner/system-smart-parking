// src/app/views/parking-service/rates/rate-detail/rate-detail.component.ts
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { EMPTY, catchError, exhaustMap, filter } from 'rxjs';
import { liveRefresh } from '../../../../core/utils/live-refresh.util';
import { describeBilling, formatMinutes } from '../../../../core/utils/rate.util';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';

// Services
import { RateService } from '../../../../core/services/parking-service/rate.service';

// Models
import { Rate, RateSimulation } from '../../../../core/models/parking-service/rate.model';

// Components
import { AlertMessageComponent } from '../../../../shared/components/alert-message/alert-message.component';

@Component({
  selector: 'app-rate-detail',
  standalone: true,
  imports: [
    CommonModule,
    AlertMessageComponent
  ],
  templateUrl: './rate-detail.component.html',
  styleUrls: ['./rate-detail.component.css']
})
export class RateDetailComponent implements OnInit {

  rateId!: number;
  rate: Rate | null = null;
  isLoading = true;
  errorMessage: string | null = null;
  showDeleteConfirm = false;
  simulation: RateSimulation | null = null;
  formatMinutes = formatMinutes;

  private destroyRef = inject(DestroyRef);

  constructor(
    private rateService: RateService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.rateId = Number(this.route.snapshot.paramMap.get('id'));
    this.loadRate();
    this.startLiveRefresh();
  }

  /** Mantiene el detalle al día (cambios de otro usuario) sin spinner ni recargar */
  private startLiveRefresh(): void {
    liveRefresh(5000).pipe(
      filter(() => !this.isLoading && !!this.rate),
      exhaustMap(() => this.rateService.getById(this.rateId, true).pipe(
        catchError((error: HttpErrorResponse) => {
          if (error.status === 404) {
            this.rate = null;
            this.errorMessage = 'Esta tarifa fue eliminada. Puedes restaurarla desde Eliminadas.';
          }
          return EMPTY;
        })
      )),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(rate => this.showRate(rate));
  }

  /** Muestra la tarifa y recalcula los ejemplos solo si cambiaron sus reglas */
  private showRate(rate: Rate): void {
    const before = this.rate;
    this.rate = rate;
    const sameRules = before && this.simulation
      && before.amount === rate.amount && before.billingUnitMinutes === rate.billingUnitMinutes
      && before.minimumMinutes === rate.minimumMinutes && before.graceMinutes === rate.graceMinutes
      && before.toleranceMinutes === rate.toleranceMinutes;
    if (sameRules) return;
    this.rateService.simulate({
      amount: rate.amount,
      billingUnitMinutes: rate.billingUnitMinutes,
      minimumMinutes: rate.minimumMinutes,
      graceMinutes: rate.graceMinutes,
      toleranceMinutes: rate.toleranceMinutes,
    }, [4, 5, 30, 65, 66, 125, 126, 480]).subscribe({
      next: simulation => this.simulation = simulation,
      error: () => this.simulation = null
    });
  }

  billingLabel(rate: Rate): string {
    return describeBilling(rate);
  }

  private loadRate(): void {
    this.isLoading = true;
    
    this.rateService.getById(this.rateId).subscribe({
      next: (rate: Rate) => {
        this.isLoading = false;
        this.showRate(rate);
      },
      error: (error) => {
        this.isLoading = false;
        this.errorMessage = 'Error al cargar los detalles de la tarifa';
        console.error(error);
      }
    });
  }

  goBack(): void {
    this.router.navigate(['/rates']);
  }



  dismissError(): void {
    this.errorMessage = null;
  }
}