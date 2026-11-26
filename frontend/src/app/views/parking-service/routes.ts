// src/app/views/parking-service/routes.ts
import { Routes } from "@angular/router";
import { AuthGuard } from "../../core/guards/auth.guard";
import { RoleGuard } from "../../core/guards/role.guard";

export const parkingRoutes: Routes = [
  // ==================== CONFIGURACIÓN BASE ====================
  // TURNOS
  {
    path: "shifts",
    loadChildren: () => import("./shifts/routes").then((m) => m.routes),
    canActivate: [AuthGuard],
  },

  // TARIFAS
  {
    path: "rates",
    loadChildren: () => import("./rates/routes").then((m) => m.routes),
    canActivate: [AuthGuard],
  },

  // PARQUEOS
  {
    path: "parkings",
    loadChildren: () => import("./parkings/routes").then((m) => m.routes),
    canActivate: [AuthGuard],
  },

  // MAPA DE OCUPACIÓN EN TIEMPO REAL
  {
    path: "occupancy",
    loadComponent: () => import("./occupancy/occupancy-map.component").then((m) => m.OccupancyMapComponent),
    canActivate: [AuthGuard, RoleGuard],
    data: { title: "Mapa de ocupación", requiredPermissions: ["occupancy.view"] },
  },

  // ZONAS (y sus espacios): se llega desde el detalle del parqueo
  {
    path: "zones",
    loadChildren: () => import("./zones/routes").then((m) => m.routes),
    canActivate: [AuthGuard],
  },

  // TIPOS DE DOCUMENTO
  {
    path: "document-types",
    loadChildren: () => import("./document-types/routes").then((m) => m.routes),
    canActivate: [AuthGuard],
  },

  // ==================== CLIENTES Y VEHÍCULOS ====================
  // CLIENTES
  {
    path: "customers",
    loadChildren: () => import("./customers/routes").then((m) => m.routes),
    canActivate: [AuthGuard],
  },

  // VEHÍCULOS
  {
    path: "vehicles",
    loadChildren: () => import("./vehicles/routes").then((m) => m.routes),
    canActivate: [AuthGuard],
  },

  // ==================== OPERACIONES DIARIAS ====================
  // TRANSACCIONES
  {
    path: "transactions",
    loadChildren: () => import("./transactions/routes").then((m) => m.routes),
    canActivate: [AuthGuard],
  },


  // ==================== INFRACCIONES ====================
  // INFRACCIONES (pendiente de implementar)
  {
    path: "infractions",
    loadChildren: () => import("./infractions/routes").then((m) => m.routes),
    canActivate: [AuthGuard],
  },

  // ==================== REPORTES ====================
  // REPORTES (pendiente de implementar)
  {
    path: "reports",
    loadChildren: () => import("./reports/routes").then((m) => m.routes),
    canActivate: [AuthGuard],
  },

  // ==================== RUTA POR DEFECTO ====================
  // Ruta por defecto - redireccionar a transacciones activas
  {
    path: "",
    redirectTo: "transactions/active",
    pathMatch: "full",
  },
];