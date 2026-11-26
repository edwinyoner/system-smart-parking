<div align="center">

# System Smart Parking - Panel web

![Angular](https://img.shields.io/badge/Angular-20-DD0031?style=for-the-badge&logo=angular&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript-5.9-3178C6?style=for-the-badge&logo=typescript&logoColor=white)
![CoreUI](https://img.shields.io/badge/CoreUI-5.5-1F3B5C?style=for-the-badge&logo=coreui&logoColor=white)
![CSS](https://img.shields.io/badge/CSS-3-1572B6?style=for-the-badge&logo=css3&logoColor=white)
![Font Awesome](https://img.shields.io/badge/Font_Awesome-7-528DD7?style=for-the-badge&logo=fontawesome&logoColor=white)

</div>

Panel administrativo del prototipo de Smart Parking (tesis UNASAM) para administradores,
autoridades y operadores. Prototipo académico; no es un sistema oficial de la Municipalidad
Provincial de Huaraz.

---

## Qué permite hacer

| Área | Vistas |
|---|---|
| **Inicio** | Dashboard en vivo (libres, ocupación, vehículos dentro, cobrado hoy, turno del usuario) y **mapa de ocupación** por espacio |
| **Operación** | Registrar entrada y salida, vehículos dentro, **Por revisar** (lecturas dudosas de las cámaras), detalle de la estadía, cobro antes de salir |
| **Parqueos** | Asistente de 5 pasos (parqueo, zonas, espacios, horario, operadores), detalle con zonas, horario y operadores de turno, espacios en lote |
| **Catálogos** | Turnos, tarifas (con simulador), tipos de documento, infracciones |
| **Pagos** | Tipos de pago, historial de pagos, devoluciones y anulaciones, pasarela QR de prueba |
| **Seguridad** | Usuarios, roles dinámicos, matriz de permisos, mi perfil |

Comportamiento común:

- **Permisos del rol activo**: menú, rutas y botones aparecen según el permiso
  (`*appHasPermission`, `RoleGuard`). Si un administrador quita un permiso, el panel lo refleja
  sin volver a entrar.
- **Sesión sincronizada**: se consulta `GET /users/me` cada 10 s; una cuenta desactivada, un
  cambio de contraseña o un rol retirado cierran la sesión con el motivo.
- **Listas y detalles en vivo** (5-10 s, sin spinner) y **papelera** con restaurar en cada
  módulo que la tiene (permiso `<modulo>.restore`).
- **Modo claro, oscuro y automático.**
- No hay registro público: los usuarios los crea un administrador.

---

## Requisitos

- Node.js 22 y npm 10
- Angular CLI 20: `npm install -g @angular/cli@20`
- El backend en marcha detrás del api-gateway (`http://localhost:8080/api`). Ver
  [`backend/README.md`](../backend/README.md).

## Ejecutar

```bash
cd frontend
npm install
npm start          # ng serve -o  →  http://localhost:4200
```

El CORS del gateway solo admite `http://localhost:4200`: usa ese puerto en desarrollo.

La URL del gateway está en `src/environments/environment.development.ts` (`apiUrl`).

## Scripts

| Comando | Qué hace |
|---|---|
| `npm start` | Servidor de desarrollo y abre el navegador |
| `npm run build` | Compila para producción en `dist/` |
| `npm run watch` | Compila en modo desarrollo al guardar |
| `npm test` | Pruebas unitarias (Karma) |

---

## Estructura

```text
src/
├── app/
│   ├── core/
│   │   ├── guards/          AuthGuard, NoAuthGuard, RoleGuard
│   │   ├── interceptors/    auth (token y renovación), error, loading
│   │   ├── models/          auth-service/, parking-service/, payment-service/
│   │   ├── services/        auth-service/, parking-service/, payment-service/
│   │   └── utils/           reglas compartidas (placa, documento, turnos, tarifas, permisos...)
│   ├── layout/              cabecera, menú lateral, pie
│   ├── shared/              componentes reutilizables
│   └── views/
│       ├── auth-service/    auth, users, roles, permissions, profile
│       ├── parking-service/ parkings, zones, shifts, rates, document-types, transactions,
│       │                    occupancy, infractions, customers, vehicles, reports
│       ├── payment-service/ payment-types, payments
│       ├── dashboard/, docs/, support/, about/
├── environments/
├── scss/                    solo la paleta y los mixins de CoreUI (Sass)
└── styles/                  estilos propios en CSS (global.css, auth.css)
```

## Convenciones

- Componentes **standalone** y TypeScript **strict**.
- Estilos en **CSS puro**; Sass solo para configurar CoreUI (`src/scss/`).
- Paleta azul marino: `#0B1F3A` (menú), `#1F3B5C` (marca, enlaces, foco). Botones por acción:
  verde crear o guardar, naranja editar, rojo eliminar, azul claro ver.
- En modo oscuro, nada de colores claros fijos: variables `--cui-*` del tema.
- Paginación con HTML nativo de Bootstrap y peticiones `?page=0&size=20`.
- Validación con `[class.is-invalid]`.
- Sin emojis en la interfaz: íconos de Font Awesome.

---

**Winner Systems Corporation S.A.C.** · Huaraz, Áncash, Perú · [www.winner-systems.com](https://www.winner-systems.com)
