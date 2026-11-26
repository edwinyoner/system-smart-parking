# api-gateway

Spring Cloud Gateway sobre WebFlux (`spring-cloud-starter-gateway-server-webflux`), puerto
**8080**. Es la **única puerta pública** del sistema: Angular, Flutter y las ESP32-CAM entran
por aquí.

## Requisitos

config-server (8888) y eureka-server (8761) en marcha. Las rutas están en `api-gateway.yml` del
config-server (prefijo `spring.cloud.gateway.server.webflux.*`).

## Rutas

Convención: `/api/<microservicio>/...`; el gateway quita el prefijo con `RewritePath`.

| Ruta pública | Destino |
|---|---|
| `/api/auth/**` | auth-service `/**` |
| `/api/users/**`, `/api/roles/**`, `/api/permissions/**` | auth-service |
| `/api/parking-service/v1/**` (incluye `/public/**`, sin token) | parking-service `/**` |
| `/api/payment-service/v1/**` | payment-service `/**` |
| `/api/iot-service/v1/captures/**`, `/api/iot-service/v1/displays/availability` | iot-service `/api/v1/...` (URI fija `IOT_SERVICE_URL`, por defecto `http://localhost:8083`) |

**Bloqueadas (404):** `/api/auth/service-token` y `/api/parking-service/v1/internal/**`. Solo se
usan en la red interna, entre servicios.

No se publican: la analítica y el borrado de caché de iot-service, ni ia-service.

## Seguridad

- `discovery.locator` desactivado: un servicio registrado en Eureka no queda expuesto por su
  nombre.
- Actuator solo con `health` y sin detalles; logs en INFO.
- `ClientIpFilter`: descarta `X-Forwarded-For`, `X-Real-IP` y `Forwarded` que mande el cliente y
  pone la IP real. Así nadie puede falsear su IP ante el límite de peticiones de iot-service.
- `CorsConfig`: solo admite `http://localhost:4200` (panel Angular) con GET, POST, PUT, PATCH y
  DELETE. La app Flutter y las ESP32 no son navegadores y no necesitan CORS.

## Ejecutar

```bash
./mvnw spring-boot:run
```

## Verificar

```bash
curl -s localhost:8080/actuator/health
curl -s localhost:8080/api/parking-service/v1/public/parkings
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/api/auth/service-token   # 404
```
