// src/app/layout/default-layout/_nav.ts
import { INavData } from "@coreui/angular";

export interface INavDataWithPermissions extends INavData {
  requiredPermissions?: string[];
}

export const navItems: INavDataWithPermissions[] = [
  // ==================== NO TOCAR - INICIO ====================
  // PANEL PRINCIPAL
  {
    title: true,
    name: "PANEL PRINCIPAL",
    class: "nav-title",
  },
  {
    name: "Dashboard",
    url: "/dashboard",
    icon: "fa-solid fa-gauge-high",
    badge: { color: "info", text: "INICIO" },
  },
  {
    name: "Mapa de ocupación",
    url: "/occupancy",
    icon: "fa-solid fa-table-cells",
    requiredPermissions: ["occupancy.view"],
  },

  // GESTIÓN DE ACCESOS
  {
    title: true,
    name: "GESTIÓN DE ACCESOS",
    class: "nav-title",
    requiredPermissions: ["users.read"],
  },
  {
    name: "Usuarios",
    url: "/users",
    icon: "fa-solid fa-user",
    requiredPermissions: ["users.read"],
  },
  {
    name: "Roles",
    url: "/roles",
    icon: "fa-solid fa-shield-halved",
    requiredPermissions: ["roles.read"],
  },
  {
    name: "Permisos",
    url: "/permissions",
    icon: "fa-solid fa-lock",
    requiredPermissions: ["permissions.read"],
  },

  // ==================== GESTIÓN DE PARKING ====================
  {
    title: true,
    name: "GESTIÓN DE PARKING",
    class: "nav-title",
  },
  {
    name: "Turnos",
    url: "/shifts",
    icon: "fa-solid fa-clock-rotate-left",
    requiredPermissions: ["shifts.read"],
  },
  {
    name: "Tarifas",
    url: "/rates",
    icon: "fa-solid fa-coins",
    requiredPermissions: ["rates.read"],
  },
  {
    name: "Parqueos",
    url: "/parkings",
    icon: "fa-solid fa-parking",
    requiredPermissions: ["parkings.read"],
  },
  {
    name: "Tipos de Documento",
    url: "/document-types",
    icon: "fa-solid fa-id-card",
    requiredPermissions: ["document-types.read"],
  },
  {
    name: "Tipos de Pago",
    url: "/payment-types",
    icon: "fa-solid fa-money-check",
    requiredPermissions: ["payment-types.read"],
  },

  // ==================== CLIENTES Y VEHÍCULOS ====================
  {
    title: true,
    name: "CLIENTES Y VEHÍCULOS",
    class: "nav-title",
  },
  {
    name: "Clientes",
    url: "/customers",
    icon: "fa-solid fa-users",
    badge: { color: "info", text: "NUEVO" },
  },
  {
    name: "Vehículos",
    url: "/vehicles",
    icon: "fa-solid fa-car",
    badge: { color: "info", text: "NUEVO" },
  },

  // ==================== OPERACIONES DIARIAS ====================
  {
    title: true,
    name: "OPERACIONES DIARIAS",
    class: "nav-title",
  },
  {
    name: "Vehículos dentro",
    url: "/transactions/active",
    icon: "fa-solid fa-clock",
    requiredPermissions: ["transactions.view"],
  },
  {
    name: "Por revisar",
    url: "/transactions/reviews",
    icon: "fa-solid fa-camera",
    requiredPermissions: ["transactions.view"],
  },
  {
    name: "Registrar Entrada",
    url: "/transactions/entry",
    icon: "fa-solid fa-right-to-bracket",
    requiredPermissions: ["transactions.entry"],
  },
  {
    name: "Registrar Salida",
    url: "/transactions/exit",
    icon: "fa-solid fa-right-from-bracket",
    requiredPermissions: ["transactions.exit"],
  },
  {
    name: "Procesar Pago",
    url: "/transactions/payment",
    icon: "fa-solid fa-money-bill-wave",
    requiredPermissions: ["payments.process"],
  },
  {
    name: "Historial Transacciones",
    url: "/transactions",
    icon: "fa-solid fa-list",
    requiredPermissions: ["transactions.view"],
  },

  // ==================== INFRACCIONES ====================
  {
    title: true,
    name: "CONTROL DE INFRACCIONES",
    class: "nav-title",
  },
  {
    name: "Infracciones",
    url: "/infractions",
    icon: "fa-solid fa-triangle-exclamation",
    requiredPermissions: ["infractions.read"],
  },
  {
    name: "Registrar Infracción",
    url: "/infractions/create",
    icon: "fa-solid fa-file-circle-plus",
    requiredPermissions: ["infractions.create"],
  },

  // ==================== GESTIÓN DE PAGOS ====================
  {
    title: true,
    name: "GESTIÓN DE PAGOS",
    class: "nav-title",
  },
  {
    name: "Historial de Pagos",
    url: "/payments",
    icon: "fa-solid fa-receipt",
    requiredPermissions: ["payments.view"],
  },

  // ==================== REPORTES ====================
  {
    title: true,
    name: "REPORTES",
    class: "nav-title",
  },
  {
    name: "Reporte Diario",
    url: "/reports/daily",
    icon: "fa-solid fa-calendar-day",
  },
  {
    name: "Reporte de Ingresos",
    url: "/reports/revenue",
    icon: "fa-solid fa-sack-dollar",
  },
  {
    name: "Reporte de Ocupación",
    url: "/reports/occupancy",
    icon: "fa-solid fa-chart-pie",
  },

  // ==================== NO TOCAR - INICIO ====================
  // SOPORTE
  {
    title: true,
    name: "SOPORTE",
    class: "nav-title",
  },
  {
    name: "Soporte Técnico",
    url: "/support",
    icon: "fa-solid fa-headset",
  },

  // DOCUMENTACIÓN
  {
    title: true,
    name: "DOCUMENTACIÓN",
    class: "nav-title",
  },
  {
    name: "Manual de Usuario",
    url: "/docs/user-manual",
    icon: "fa-solid fa-book",
  },
  {
    name: "Acerca del Sistema",
    url: "/about",
    icon: "fa-solid fa-circle-info",
  },
  // ==================== NO TOCAR - FIN ====================
];