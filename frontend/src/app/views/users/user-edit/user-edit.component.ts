import { Component, OnInit } from "@angular/core";
import { CommonModule } from "@angular/common";
import {
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from "@angular/forms";
import { Router, ActivatedRoute } from "@angular/router";
import { UserService } from "../../../core/services/user.service";
import { CurrentUserService } from "../../../core/services/current-user.service";
import { UserProtectionService } from "../../../core/services/user-protection.service";
import { RoleService } from "../../../core/services/role.service";
import { AlertMessageComponent } from "../../../shared/components/alert-message/alert-message.component";
import { environment } from "../../../../environments/environment";
import { generatePassword, maskPassword } from "../../../core/utils/password.util";
import { Role } from "../../../core/models/role.model";
import { User } from "../../../core/models/user.model";
import { UpdateUserRequest } from "../../../core/models/user.model";

/**
 * Componente para editar usuarios existentes
 */
@Component({
  selector: "app-user-edit",
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, AlertMessageComponent],
  templateUrl: "./user-edit.component.html",
  styleUrls: ["./user-edit.component.scss"],
})
export class UserEditComponent implements OnInit {
  userForm!: FormGroup;
  userId!: number;
  user: User | null = null;
  roles: Role[] = [];
  blockedReason: string | null = null;
  isLoading = false;
  isLoadingData = false;
  successMessage: string | null = null;
  errorMessage: string | null = null;

  // Nueva contraseña generada
  newGeneratedPassword: string | null = null;
  showPasswordSection = false;
  readonly loginUrl = `${environment.frontendUrl}/login`;

  constructor(
    private fb: FormBuilder,
    private userService: UserService,
    private roleService: RoleService,
    private currentUserService: CurrentUserService,
    private userProtection: UserProtectionService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.initForm();
    this.getUserId();
    this.loadRoles();
    this.loadUserData();
  }

  /**
   * Obtiene el ID del usuario desde la ruta
   */
  private getUserId(): void {
    const id = this.route.snapshot.paramMap.get("id");
    if (id) {
      this.userId = +id;
    } else {
      this.router.navigate(["/users"]);
    }
  }

  /**
   * Inicializa el formulario
   */
  private initForm(): void {
    this.userForm = this.fb.group({
      firstName: [
        "",
        [
          Validators.required,
          Validators.minLength(2),
          Validators.maxLength(100),
        ],
      ],
      lastName: [
        "",
        [
          Validators.required,
          Validators.minLength(2),
          Validators.maxLength(100),
        ],
      ],
      email: [{ value: "", disabled: true }], // Email no editable
      phoneNumber: ["", [Validators.pattern(/^\+?[1-9]\d{1,14}$/)]],
      status: [true],
      roleIds: [[], [Validators.required]],
    });
  }

  /**
   * Carga los datos del usuario
   */
  private loadUserData(): void {
    this.isLoadingData = true;

    this.userService.getUserById(this.userId).subscribe({
      next: (user) => {
        this.user = user;
        this.userForm.patchValue({
          firstName: user.firstName,
          lastName: user.lastName,
          email: user.email,
          phoneNumber: user.phoneNumber || "",
          status: user.status,
        });
        this.blockedReason = this.userProtection.getBlockedReason(user);
        this.markAssignedRoles();
        this.isLoadingData = false;
      },
      error: (error) => {
        this.isLoadingData = false;
        this.errorMessage = "Error al cargar datos del usuario";
        console.error(error);
      },
    });
  }

  /**
   * Marca los roles que ya tiene el usuario.
   * Se comparan por nombre: el backend no envía el id del rol dentro del usuario.
   * Se llama al terminar cada carga, porque no se sabe cuál llega primero.
   */
  private markAssignedRoles(): void {
    if (!this.user || this.roles.length === 0) return;

    const assigned = this.user.roles.map((r) => r.name);
    this.userForm.patchValue({
      roleIds: this.roles.filter((r) => assigned.includes(r.name)).map((r) => r.id),
    });
  }

  /**
   * Indica si el check de un rol no se puede modificar
   */
  isRoleDisabled(role: Role): boolean {
    return !!this.blockedReason
      || (role.name === "ADMIN" && !this.userProtection.canManageAdmins());
  }

  /**
   * Carga la lista de roles disponibles
   */
  private loadRoles(): void {
    this.roleService.getAllRoles().subscribe({
      next: (roles) => {
        this.roles = roles;
        this.markAssignedRoles();
      },
      error: (error) => {
        console.error("Error al cargar roles:", error);
      },
    });
  }

  /**
   * Genera una nueva contraseña
   */
  onGenerateNewPassword(): void {
    this.newGeneratedPassword = generatePassword();
    this.showPasswordSection = true;
  }

  /**
   * Maneja el cambio de selección de roles
   */
  onRoleChange(roleId: number, event: any): void {
    const roleIds = [...(this.userForm.get("roleIds")?.value || [])];

    if (event.target.checked) {
      if (!roleIds.includes(roleId)) {
        roleIds.push(roleId);
      }
    } else {
      const index = roleIds.indexOf(roleId);
      if (index > -1) {
        roleIds.splice(index, 1);
      }
    }

    this.userForm.patchValue({ roleIds });
  }

  /**
   * Verifica si un rol está seleccionado
   */
  isRoleSelected(roleId: number): boolean {
    const roleIds = this.userForm.get("roleIds")?.value || [];
    return roleIds.includes(roleId);
  }

  /**
   * Maneja el toggle de estado
   */
  onStatusToggle(event: any): void {
    const isActive = event.target.checked;
    this.userForm.patchValue({ status: isActive });
  }

  onSubmit(): void {
    if (this.userForm.invalid) {
      this.userForm.markAllAsTouched();
      return;
    }

    this.isLoading = true;
    this.successMessage = null;
    this.errorMessage = null;

    // Convertir roleIds (numbers) a nombres de roles (strings)
    const selectedRoleIds = this.userForm.get("roleIds")?.value || [];
    const roleNames = selectedRoleIds
      .map((id: number) => {
        const role = this.roles.find((r) => r.id === id);
        return role?.name || "";
      })
      .filter((name: string) => name !== "");

    const updateData: UpdateUserRequest = {
      firstName: this.userForm.get("firstName")?.value,
      lastName: this.userForm.get("lastName")?.value,
      phoneNumber: this.userForm.get("phoneNumber")?.value || "", // "" = borrar el teléfono
      status: this.userForm.get("status")?.value,
      roles: roleNames, // ["ADMIN", "AUTORIDAD"]
    };

    this.userService.updateUser(this.userId, updateData).subscribe({
      next: (response) => {
        this.isLoading = false;

        // Si se editó a sí mismo, actualizar cabecera, menú y perfil
        if (this.currentUserService.getCurrentUser()?.id === this.userId) {
          this.currentUserService.applyProfile(response);
        }

        this.successMessage = "Usuario actualizado exitosamente";

        // Redirigir después de 1.5s
        setTimeout(() => {
          this.router.navigate(["/users"]);
        }, 1500);
      },
      error: (error) => {
        this.isLoading = false;
        this.errorMessage =
          error.error?.message || "Error al actualizar el usuario";
        console.error("Error completo:", error);
      },
    });
  }

  /**
   * Contraseña generada con solo los 3 últimos caracteres visibles
   */
  get maskedPassword(): string {
    return this.newGeneratedPassword ? maskPassword(this.newGeneratedPassword) : "";
  }

  /**
   * Guarda la nueva contraseña y la envía al correo del usuario
   */
  sendCredentialsByEmail(): void {
    if (!this.newGeneratedPassword || !this.user) return;

    const email = this.user.email;
    this.isLoading = true;
    this.successMessage = null;
    this.errorMessage = null;

    this.userService.resendCredentials(this.userId, this.newGeneratedPassword).subscribe({
      next: () => {
        this.isLoading = false;
        this.discardPassword();
        this.successMessage = `Credenciales enviadas a ${email}`;
        window.scrollTo({ top: 0 });
      },
      error: (error) => {
        this.isLoading = false;
        this.errorMessage = error.error?.message || "Error al enviar las credenciales";
        window.scrollTo({ top: 0 });
      },
    });
  }

  /**
   * Descarta la contraseña generada para no conservarla en memoria
   */
  discardPassword(): void {
    this.newGeneratedPassword = null;
    this.showPasswordSection = false;
  }

  /**
   * Vuelve a la lista de usuarios
   */
  cancel(): void {
    this.router.navigate(["/users"]);
  }

  /**
   * Verifica si un campo tiene error
   */
  hasError(fieldName: string, errorType: string): boolean {
    const field = this.userForm.get(fieldName);
    return !!(field?.hasError(errorType) && (field?.dirty || field?.touched));
  }

  /**
   * Obtiene el mensaje de error para un campo
   */
  getErrorMessage(fieldName: string): string {
    const field = this.userForm.get(fieldName);

    if (field?.hasError("required")) return "Este campo es requerido";
    if (field?.hasError("email")) return "Email inválido";
    if (field?.hasError("minlength")) {
      const minLength = field.errors?.["minlength"].requiredLength;
      return `Debe tener al menos ${minLength} caracteres`;
    }
    if (field?.hasError("maxlength")) {
      const maxLength = field.errors?.["maxlength"].requiredLength;
      return `No puede exceder ${maxLength} caracteres`;
    }
    if (field?.hasError("pattern"))
      return "Formato inválido. Ejemplo: +51987654321";

    return "";
  }

  dismissSuccess(): void {
    this.successMessage = null;
  }

  dismissError(): void {
    this.errorMessage = null;
  }
}
