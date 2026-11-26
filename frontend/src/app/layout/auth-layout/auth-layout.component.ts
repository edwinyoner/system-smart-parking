import { Component } from "@angular/core";
import { RouterOutlet } from "@angular/router";
import { CommonModule } from "@angular/common";

/**
 * Layout para páginas de autenticación
 *
 * Sin sidebar: nombre del sistema arriba, formulario en una tarjeta clara y pie, para:
 * - Login
 * - Forgot Password
 * - Reset Password
 * - Verify Email
 */
@Component({
  selector: "app-auth-layout",
  standalone: true,
  imports: [CommonModule, RouterOutlet],
  templateUrl: "./auth-layout.component.html",
})
export class AuthLayoutComponent {
  currentYear: number = new Date().getFullYear();

  // Es un prototipo académico: no se presenta como sistema oficial de la municipalidad
  companyName: string = "Winner Systems Corporation S.A.C.";
  appName: string = "Smart Parking System";

  constructor() {}
}
