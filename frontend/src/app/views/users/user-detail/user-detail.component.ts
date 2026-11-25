import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, exhaustMap, filter } from 'rxjs';
import { liveRefresh } from '../../../core/utils/live-refresh.util';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { UserService } from '../../../core/services/user.service';
import { UserProtectionService } from '../../../core/services/user-protection.service';
import { User } from '../../../core/models/user.model';
import { AlertMessageComponent } from '../../../shared/components/alert-message/alert-message.component';

@Component({
  selector: 'app-user-detail',
  standalone: true,
  imports: [
    CommonModule,
    AlertMessageComponent
  ],
  templateUrl: './user-detail.component.html',
  styleUrl: './user-detail.component.css'
})
export class UserDetailComponent implements OnInit {

  user: User | null = null;
  isLoading = false;
  errorMessage: string | null = null;
  showDeleteConfirm = false;
  deleteBlockedReason: string | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private userService: UserService,
    private userProtection: UserProtectionService
  ) {}

  private destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    this.loadUser();
    this.startLiveRefresh();
  }

  /**
   * Mantiene los datos al día sin spinner ni recargar la página
   */
  private startLiveRefresh(): void {
    liveRefresh(5000).pipe(
      filter(() => !this.isLoading && !!this.user),
      exhaustMap(() => this.userService.getUserById(this.user!.id, true).pipe(
        catchError(() => EMPTY)
      )),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(user => this.user = user);
  }

  private loadUser(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    
    if (!id || isNaN(id)) {
      this.errorMessage = 'ID de usuario inválido';
      return;
    }

    this.isLoading = true;

    this.userService.getUserById(id).subscribe({
      next: (user) => {
        this.user = user;
        this.isLoading = false;
      },
      error: (error) => {
        this.isLoading = false;
        this.errorMessage = error.error?.message || 'Error al cargar el usuario';
        console.error('Error:', error);
      }
    });
  }

  goBack(): void {
    this.router.navigate(['/users']);
  }

  editUser(): void {
    if (this.user) {
      this.router.navigate(['/users', this.user.id, 'edit']);
    }
  }

  confirmDelete(): void {
    if (!this.user) return;

    this.deleteBlockedReason = this.userProtection.getBlockedReason(this.user);
    this.showDeleteConfirm = true;
  }

  cancelDelete(): void {
    this.showDeleteConfirm = false;
  }

  deleteUser(): void {
    if (!this.user) return;

    this.isLoading = true;
    this.showDeleteConfirm = false;

    this.userService.deleteUser(this.user.id).subscribe({
      next: () => {
        this.router.navigate(['/users'], {
          state: { 
            successMessage: `Usuario "${this.user?.firstName} ${this.user?.lastName}" eliminado exitosamente` 
          }
        });
      },
      error: (error) => {
        this.isLoading = false;
        this.errorMessage = error.error?.message || 'Error al eliminar el usuario';
      }
    });
  }

  getUserInitials(user: User): string {
    const firstInitial = user.firstName?.charAt(0) || '';
    const lastInitial = user.lastName?.charAt(0) || '';
    return `${firstInitial}${lastInitial}`.toUpperCase();
  }

  formatDate(date: string | Date | null | undefined): string {
    if (!date) return 'N/A';
    return new Date(date).toLocaleDateString('es-PE', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  dismissError(): void {
    this.errorMessage = null;
  }
}