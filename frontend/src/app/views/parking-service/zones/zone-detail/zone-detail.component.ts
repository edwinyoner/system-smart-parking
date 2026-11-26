import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EMPTY, catchError, exhaustMap } from 'rxjs';
import { ZoneService } from '../../../../core/services/parking-service/zone.service';
import { AuthContextService } from '../../../../core/services/auth-service/auth-context.service';
import { Zone } from '../../../../core/models/parking-service/zone.model';
import { AlertMessageComponent } from '../../../../shared/components/alert-message/alert-message.component';
import { takeSuccessMessage } from '../../../../core/utils/flash-message.util';
import { liveRefresh } from '../../../../core/utils/live-refresh.util';
import { apiError, locationStatus } from '../../../../core/utils/parking.util';
import { ZoneSpacesComponent } from './zone-spaces/zone-spaces.component';

/** Detalle de la zona: datos, capacidad en vivo y gestión de sus espacios */
@Component({
  selector: 'app-zone-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, AlertMessageComponent, ZoneSpacesComponent],
  templateUrl: './zone-detail.component.html',
  styleUrls: ['./zone-detail.component.css'],
})
export class ZoneDetailComponent implements OnInit {

  zoneId!: number;
  zone: Zone | null = null;
  loading = true;
  errorMessage: string | null = null;
  successMessage: string | null = null;
  showDelete = false;

  readonly status = locationStatus;
  private destroyRef = inject(DestroyRef);

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private zoneService: ZoneService,
    private authContext: AuthContextService,
  ) {
    this.successMessage = takeSuccessMessage();
  }

  ngOnInit(): void {
    this.zoneId = Number(this.route.snapshot.paramMap.get('id'));
    this.load(false);
    liveRefresh(5000).pipe(
      exhaustMap(() => this.zoneService.getById(this.zoneId, true).pipe(catchError(() => EMPTY))),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(z => (this.zone = z));
  }

  load(silent: boolean): void {
    this.zoneService.getById(this.zoneId, silent).subscribe({
      next: z => { this.loading = false; this.zone = z; },
      error: err => { this.loading = false; this.errorMessage = apiError(err, 'No se pudo cargar la zona'); },
    });
  }

  can(permission: string): boolean {
    return this.authContext.hasPermission(permission);
  }

  occupancyClass(pct?: number): string {
    if ((pct ?? 0) >= 90) return 'bg-danger';
    if ((pct ?? 0) >= 70) return 'bg-warning';
    return 'bg-success';
  }

  /** La tabla de espacios avisa cuando cambia algo: la capacidad se recalcula al momento */
  onSpacesChanged(): void {
    this.load(true);
  }

  deleteZone(): void {
    this.showDelete = false;
    const zone = this.zone;
    if (!zone?.id) return;
    this.zoneService.delete(zone.id).subscribe({
      next: () => this.router.navigate(['/parkings', zone.parkingId],
        { state: { successMessage: `Zona "${zone.name}" eliminada. Puedes restaurarla desde Eliminadas.` } }),
      error: err => (this.errorMessage = apiError(err, 'No se pudo eliminar la zona')),
    });
  }
}
