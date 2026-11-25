// src/app/views/parking-service/shifts/shift-detail/shift-detail.component.ts
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { EMPTY, catchError, exhaustMap, filter } from 'rxjs';
import { liveRefresh } from '../../../../core/utils/live-refresh.util';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';

// Services
import { ShiftService } from '../../../../core/services/parking-service/shift.service';

// Models
import { Shift } from '../../../../core/models/parking-service/shift.model';
import { formatShiftDuration } from '../../../../core/utils/shift.util';

// Components
import { AlertMessageComponent } from '../../../../shared/components/alert-message/alert-message.component';

@Component({
  selector: 'app-shift-detail',
  standalone: true,
  imports: [
    CommonModule,
    AlertMessageComponent
  ],
  templateUrl: './shift-detail.component.html',
  styleUrls: ['./shift-detail.component.css']
})
export class ShiftDetailComponent implements OnInit {

  shiftId!: number;
  shift: Shift | null = null;
  isLoading = true;
  errorMessage: string | null = null;
  showDeleteConfirm = false;

  private destroyRef = inject(DestroyRef);

  constructor(
    private shiftService: ShiftService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.shiftId = Number(this.route.snapshot.paramMap.get('id'));
    this.loadShift();
    this.startLiveRefresh();
  }

  /** Mantiene el detalle al día (cambios de otro usuario) sin spinner ni recargar */
  private startLiveRefresh(): void {
    liveRefresh(5000).pipe(
      filter(() => !this.isLoading && !!this.shift),
      exhaustMap(() => this.shiftService.getById(this.shiftId, true).pipe(
        catchError((error: HttpErrorResponse) => {
          if (error.status === 404) {
            // Otro usuario lo eliminó mientras se veía
            this.shift = null;
            this.errorMessage = 'Este turno fue eliminado. Puedes restaurarlo desde Eliminados.';
          }
          return EMPTY;
        })
      )),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(shift => this.shift = shift);
  }

  private loadShift(): void {
    this.isLoading = true;
    
    this.shiftService.getById(this.shiftId).subscribe({
      next: (shift: Shift) => {
        this.shift = shift;
        this.isLoading = false;
      },
      error: (error) => {
        this.isLoading = false;
        this.errorMessage = 'Error al cargar los detalles del turno';
        console.error(error);
      }
    });
  }

  goBack(): void {
    this.router.navigate(['/shifts']);
  }

  formatTime(time: string): string {
    if (!time) return 'N/A';
    return time.substring(0, 5);
  }

  /** Duración calculada por el backend, contando el minuto final (06:00 - 13:59 = 8 h) */
  durationLabel(shift: Shift): string {
    return formatShiftDuration(shift.durationHours);
  }

  dismissError(): void {
    this.errorMessage = null;
  }
}