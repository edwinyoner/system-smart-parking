# Mobile - System Smart Parking

Aplicaciones móviles del prototipo (tesis UNASAM).

| Carpeta | App | Estado |
|---|---|---|
| [`smart_parking/`](smart_parking/README.md) | App Flutter para conductores: parqueos en lista y mapa, disponibilidad en vivo, zonas, horario, tarifa, mapa de espacios y "Cómo llegar" | Versión 1 (pública, sin login) |

Decisiones:

- **Versión 1 pública y sin login**: solo muestra información pública de los parqueos
  (`/api/parking-service/v1/public/**`). No consulta estadías, pagos ni datos por placa o por
  documento, por privacidad.
- La deuda de un estacionamiento en la calle se paga en el kiosco o con el operador, no desde la
  app.
- **Trabajo futuro**: cuenta del conductor con Google en un microservicio aparte
  (`driver-service`).
