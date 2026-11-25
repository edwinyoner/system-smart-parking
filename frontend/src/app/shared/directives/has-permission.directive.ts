import { Directive, Input, TemplateRef, ViewContainerRef, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { PermissionService } from '../../core/services/auth-service/permission.service';
import { CurrentUserService } from '../../core/services/auth-service/current-user.service';

/**
 * Directiva estructural para mostrar/ocultar elementos según permisos
 * 
 * Uso:
 * <button *hasPermission="'users.create'">Crear Usuario</button>
 * <div *hasPermission="['users.create', 'users.update']">...</div>
 * 
 * Por defecto usa lógica OR (al menos uno de los permisos)
 * Para lógica AND usa: *hasPermission="perms; requireAll: true"
 * 
 * <button *appHasPermission="'users.create'">Crear</button>
 * <button *appHasPermission="['users.update', 'users.delete']" [requireAll]="false">Editar o Eliminar</button>
 * 
 * Directiva estructural para mostrar/ocultar elementos según roles
 * 
 * Uso:
 * <div *appHasRole="'ADMIN'">Solo para admin</div>
 * <div *appHasRole="['ADMIN', 'AUTORIDAD']">Para admin o autoridad</div>
 */
@Directive({
  selector: '[appHasPermission]',
  standalone: true
})
export class HasPermissionDirective implements OnInit, OnDestroy {
  
  private permissions: string[] = [];
  private requireAll = false;
  private hasView = false;
  private subscription?: Subscription;

  constructor(
    private templateRef: TemplateRef<any>,
    private viewContainer: ViewContainerRef,
    private permissionService: PermissionService,
    private currentUserService: CurrentUserService
  ) {}

  @Input()
  set appHasPermission(permissions: string | string[]) {
    this.permissions = Array.isArray(permissions) ? permissions : [permissions];
    this.updateView();
  }

  @Input()
  set appHasPermissionRequireAll(value: boolean) {
    this.requireAll = value;
    this.updateView();
  }

  ngOnInit(): void {
    // Se reevalúa cuando cambian los permisos (sincronización de sesión)
    this.subscription = this.currentUserService.currentUser$.subscribe(() => this.updateView());
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  private updateView(): void {
    const hasPermission = this.requireAll
      ? this.permissionService.hasAllPermissions(this.permissions)
      : this.permissionService.hasAnyPermission(this.permissions);

    // Solo se crea o se quita la vista cuando cambia el resultado: evita duplicar el contenido
    if (hasPermission && !this.hasView) {
      this.viewContainer.createEmbeddedView(this.templateRef);
      this.hasView = true;
    } else if (!hasPermission && this.hasView) {
      this.viewContainer.clear();
      this.hasView = false;
    }
  }
}