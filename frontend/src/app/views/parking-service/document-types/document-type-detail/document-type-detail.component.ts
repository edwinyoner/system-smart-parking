import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { EMPTY, catchError, exhaustMap, filter } from 'rxjs';
import { liveRefresh } from '../../../../core/utils/live-refresh.util';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { DocumentTypeService } from '../../../../core/services/parking-service/document-type.service';
import { DocumentType } from '../../../../core/models/parking-service/document-type.model';
import { AlertMessageComponent } from '../../../../shared/components/alert-message/alert-message.component';

@Component({
  selector: 'app-document-type-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, AlertMessageComponent],
  templateUrl: './document-type-detail.component.html',
  styleUrls: ['./document-type-detail.component.css']
})
export class DocumentTypeDetailComponent implements OnInit {

  documentTypeId!: number;
  documentType: DocumentType | null = null;
  isLoading = true;
  errorMessage: string | null = null;

  private destroyRef = inject(DestroyRef);

  constructor(
    private documentTypeService: DocumentTypeService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.documentTypeId = Number(this.route.snapshot.paramMap.get('id'));
    this.loadDocumentType();
    this.startLiveRefresh();
  }

  /** Mantiene el detalle al día (cambios de otro usuario) sin spinner ni recargar */
  private startLiveRefresh(): void {
    liveRefresh(5000).pipe(
      filter(() => !this.isLoading && !!this.documentType),
      exhaustMap(() => this.documentTypeService.getById(this.documentTypeId, true).pipe(
        catchError((error: HttpErrorResponse) => {
          if (error.status === 404) {
            this.documentType = null;
            this.errorMessage = 'Este tipo de documento fue eliminado. Puedes restaurarlo desde Eliminados.';
          }
          return EMPTY;
        })
      )),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(dt => this.documentType = dt);
  }

  private loadDocumentType(): void {
    this.isLoading = true;
    this.documentTypeService.getById(this.documentTypeId).subscribe({
      next: (dt: DocumentType) => { this.documentType = dt; this.isLoading = false; },
      error: (err) => {
        this.isLoading    = false;
        this.errorMessage = 'Error al cargar los detalles del tipo de documento';
        console.error(err);
      }
    });
  }

  goBack(): void { this.router.navigate(['/document-types']); }
  dismissError(): void { this.errorMessage = null; }
}