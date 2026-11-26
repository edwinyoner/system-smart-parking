# config-server

Spring Cloud Config Server (puerto **8888**). Entrega la configuración de todos los servicios
Java: auth, parking, payment, eureka y api-gateway. Es el **primero** que se arranca.

## Archivos de configuración

Perfil `native`: los archivos se leen de `src/main/resources/config/`.

| Archivo | Para |
|---|---|
| `application.yml` | Común a todos: Eureka, actuator (`show-details: never`), logging |
| `api-gateway.yml` | Rutas públicas, rutas bloqueadas, URI de iot-service |
| `eureka-server.yml` | Registro de servicios |
| `auth-service.yml` | PostgreSQL, JWT, correo, reCAPTCHA, clientes de servicio |
| `parking-service.yml` | Oracle, JWT, Feign, reglas de cobro y de la IA |
| `payment-service.yml` | SQL Server, JWT, pasarela simulada, webhook |

Los valores sensibles son `${VARIABLE}` y se leen de `backend/.env` (no versionado). Ningún
secreto va escrito en estos archivos.

## Ejecutar

```bash
./mvnw spring-boot:run
```

## Verificar

```bash
curl -s localhost:8888/actuator/health
curl -s localhost:8888/auth-service/default     # configuración que recibe auth-service
```

> El config-server no tiene autenticación: en la nube debe quedar solo en la red interna.
