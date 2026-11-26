# smart_parking (app móvil)

App de Smart Parking Huaraz para conductores. **Prototipo académico** de tesis (UNASAM);
no es un servicio oficial de la Municipalidad Provincial de Huaraz.

## Qué muestra (versión 1, pública y sin login)

- Parqueos en **lista** y en **mapa** (OpenStreetMap), con búsqueda sin tildes y filtro
  "con espacios disponibles".
- Por parqueo: estado (abierto, lleno, cerrado, fuera de servicio), **disponibles, ocupados y
  total** (los mismos números que los paneles LED), zonas, horario, tarifa vigente y sus reglas.
- **Cuadrícula de espacios** por zona: disponible, ocupado o no disponible (sin placas).
- **Cómo llegar**: abre Google Maps con la ruta al parqueo.
- Se actualiza sola cada 12 s mientras la pantalla está abierta.

No pide datos personales ni crea cuentas. La cuenta del conductor (login con Google) queda
como trabajo futuro.

## Origen de los datos

Endpoints públicos de parking-service a través del api-gateway (sin token):

| Pantalla | Endpoint |
|---|---|
| Lista y mapa | `GET /api/parking-service/v1/public/parkings` |
| Detalle | `GET /api/parking-service/v1/public/parkings/{id}` |
| Espacios | `GET /api/parking-service/v1/public/parkings/{id}/spaces` |

## Estructura

```text
lib/
  app/            app.dart, router (go_router), tema (paleta azul marino)
  core/           config (API_BASE_URL), red (Dio), errores (Failure), utilidades
  features/parking/
    domain/       entidades y contrato del repositorio (sin Flutter ni Dio)
    data/         API pública, lectura del JSON y repositorio
    presentation/ providers (Riverpod), páginas y widgets
```

## Ejecutar

La URL del api-gateway es obligatoria (`Env.validate()` detiene la app si falta):

```bash
# macOS o simulador de iOS
flutter run -d macos --dart-define=API_BASE_URL=http://localhost:8080/api

# Emulador de Android (10.0.2.2 es el equipo anfitrión)
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api
```

En VS Code se puede crear `.vscode/launch.json` con esos mismos `args` (la carpeta `.vscode/`
no se versiona). No se prueba en Chrome: el CORS del gateway solo admite el panel web.

## Verificar

```bash
flutter analyze   # debe dar "No issues found!"
flutter test      # usan un repositorio falso: no necesitan el backend
```
