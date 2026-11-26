<div align="center">

# System Smart Parking - Backend

![Java](https://img.shields.io/badge/Java-25-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.7-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)
![Spring Cloud](https://img.shields.io/badge/Spring_Cloud-2025.0-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Go](https://img.shields.io/badge/Go-1.26-00ADD8?style=for-the-badge&logo=go&logoColor=white)
![Python](https://img.shields.io/badge/Python-3.12-3776AB?style=for-the-badge&logo=python&logoColor=white)
![Docker](https://img.shields.io/badge/Docker_Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)

</div>

Microservicios del prototipo de Smart Parking (tesis UNASAM). Prototipo académico; no es un
sistema oficial de la Municipalidad Provincial de Huaraz.

---

## Contenido

- [Arquitectura](#arquitectura)
- [Servicios](#servicios)
- [Rutas públicas del gateway](#rutas-públicas-del-gateway)
- [Seguridad](#seguridad)
- [Configuración y secretos](#configuración-y-secretos)
- [Ejecución local](#ejecución-local)
- [Pruebas](#pruebas)
- [Estructura](#estructura)

---

## Arquitectura

```text
                         ┌───────────────────────────────┐
 Angular / Flutter /     │        api-gateway :8080       │  única puerta pública
 ESP32-CAM ─────────────►│ rutas, CORS, IP real del cliente│  bloquea /internal/** y /service-token
                         └──┬─────────┬──────────┬───────┬┘
                            │         │          │       │ (URI fija: iot no está en Eureka)
                  auth-service  parking-service  payment-service  iot-service
                  :8081         :8082            :8085            :8083
                  PostgreSQL    Oracle           SQL Server       MySQL + Redis
                      ▲             ▲  ▲               │              │   │
                      │ Feign       │  └── /internal/charges ◄────────┘   │ multipart
                      └─────────────┤        (token de servicio)          ▼
                                    └──── /internal/captures, /sensors ── ia-service :8084
                                          (token de servicio)              MongoDB

 config-server :8888 (configuración centralizada) · eureka-server :8761 (registro de servicios)
 Mosquitto :1883 (MQTT: sensores y paneles) · MinIO :9000 (fotos de las cámaras)
```

| Tráfico | Cómo |
|---|---|
| Norte-sur (clientes) | Siempre por el gateway (`:8080`) |
| Este-oeste (entre servicios) | Directo por la red interna, con **token de servicio** |
| Dispositivos IoT | Cámaras por HTTP (necesitan respuesta inmediata de la barrera); sensores y paneles por MQTT |

---

## Servicios

| Servicio | Tecnología | Arquitectura | BD | Puerto | Responsabilidad |
|---|---|---|---|---|---|
| [config-server](infrastructure/config-server/README.md) | Spring Cloud Config | — | — | 8888 | Configuración de todos los servicios Java (`config/*.yml`) |
| [eureka-server](infrastructure/eureka-server/README.md) | Netflix Eureka | — | — | 8761 | Registro y descubrimiento |
| [api-gateway](infrastructure/api-gateway/README.md) | Spring Cloud Gateway WebFlux | — | — | 8080 | Rutas públicas, CORS, bloqueo de rutas internas |
| auth-service | Java 25 / Spring Boot | Hexagonal | PostgreSQL | 8081 | Usuarios, roles dinámicos, permisos, JWT, correo, token de servicio |
| parking-service | Java 25 / Spring Boot | Hexagonal | Oracle | 8082 | Parqueos, zonas, espacios, turnos, tarifas, operadores, transacciones, revisión de placas, ocupación, disponibilidad pública |
| payment-service | Java 25 / Spring Boot | Hexagonal | SQL Server | 8085 | Tipos de pago, cobros QR (pasarela simulada) y efectivo, devoluciones |
| [iot-service](services/iot-service/README.md) | Go / Gin | Por capas | MySQL + Redis | 8083 | Orquesta cámaras, sensores y paneles |
| [ia-service](services/ia-service/README.md) | Python / FastAPI | Por capas, sin estado | MongoDB | 8084 | Lectura de placas y ocupación aérea (solo inferencia) |

Reparto de responsabilidades en los cobros: **parking-service sabe cuánto se debe** y
**payment-service registra si se pagó**. ia-service no tiene lógica de negocio y nunca llama a
parking-service; el orquestador es iot-service.

---

## Rutas públicas del gateway

Convención: `/api/<microservicio>/...`. El gateway quita el prefijo antes de reenviar.

| Ruta pública | Destino | Ruta en el servicio |
|---|---|---|
| `/api/auth/**` | auth-service | `/**` |
| `/api/users/**`, `/api/roles/**`, `/api/permissions/**` | auth-service | `/users/**`, `/roles/**`, `/permissions/**` |
| `/api/parking-service/v1/**` | parking-service | `/**` |
| `/api/parking-service/v1/public/**` | parking-service | `/public/**` (sin token: disponibilidad) |
| `/api/payment-service/v1/**` | payment-service | `/**` |
| `/api/iot-service/v1/captures/**` | iot-service | `/api/v1/captures/**` |
| `/api/iot-service/v1/displays/availability` | iot-service | `/api/v1/displays/availability` |
| `/api/auth/service-token`, `/api/parking-service/v1/internal/**` | **bloqueadas (404)** | solo red interna |

El discovery locator está desactivado y el actuator del gateway solo expone `health`.
ia-service no se publica.

### Disponibilidad pública (sin login)

| Endpoint | Uso |
|---|---|
| `GET /api/parking-service/v1/public/parkings` | Lista con disponibles, ocupados, total, turno y tarifa vigentes |
| `GET /api/parking-service/v1/public/parkings/{id}` | Detalle con zonas y horario |
| `GET /api/parking-service/v1/public/parkings/{id}/availability` | Solo los números |
| `GET /api/parking-service/v1/public/parkings/{id}/spaces` | Mapa de espacios (disponible, ocupado, no disponible; sin placas) |

Total = disponibles + ocupados (los espacios en mantenimiento o fuera de servicio no cuentan).
Si el parqueo no atiende a esa hora, los disponibles son 0.

---

## Seguridad

- **JWT de usuario** emitido por auth-service. Las autoridades son los permisos del **rol activo**
  (`users.create`, `parkings.read`, ...). Las rutas se protegen por permiso, no por nombre de rol.
- **Token de servicio** (`POST /service-token`, 5 minutos) para iot-service y payment-service.
  Solo lleva permisos `internal.*` y no sirve en rutas de usuario; un token de usuario tampoco
  sirve en `/internal/**`.
- **Webhook de la pasarela** firmado con HMAC-SHA256.
- **MQTT** con usuario por dispositivo y ACL: cada sensor solo escribe en su tema y cada panel
  solo lee el suyo.
- El gateway descarta `X-Forwarded-For` del cliente y pone la IP real (base del límite de
  peticiones de iot-service).

---

## Configuración y secretos

- La configuración de los servicios Java está en
  `infrastructure/config-server/src/main/resources/config/` (`application.yml` común y un
  `<servicio>.yml` por servicio). Los valores sensibles son `${VARIABLE}`.
- Los secretos se leen de **`backend/.env`**, que **no se versiona**. Copia la plantilla y
  complétala:

  ```bash
  cp .env.example .env
  ```

  Variables principales: `JWT_SECRET`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `MAIL_USERNAME`,
  `MAIL_PASSWORD`, `PAYMENT_SERVICE_CLIENT_SECRET`, `IOT_SERVICE_CLIENT_SECRET`,
  `PAYMENT_GATEWAY_WEBHOOK_SECRET`, `MSSQL_*`, `MQTT_IOT_PASSWORD` (los secretos, de 32
  caracteres o más).
- iot-service e ia-service tienen su propio `.env` (plantilla `.env.example` en cada carpeta).
- En desarrollo, `ddl-auto: create-drop`: cada reinicio de auth o parking recrea las tablas y el
  seeder vuelve a cargar los datos iniciales (admin, roles, permisos, turnos, tarifas).

---

## Ejecución local

### 1. Contenedores

```bash
docker compose up -d          # ver README-DOCKER.md
```

### 2. Servicios, en este orden

```bash
# Java: cada uno desde su carpeta. En IntelliJ, la configuración de ejecución debe cargar backend/.env
cd infrastructure/config-server && ./mvnw spring-boot:run    # 8888
cd infrastructure/eureka-server && ./mvnw spring-boot:run    # 8761
cd infrastructure/api-gateway   && ./mvnw spring-boot:run    # 8080
cd services/auth-service        && ./mvnw spring-boot:run    # 8081
cd services/parking-service     && ./mvnw spring-boot:run    # 8082
cd services/payment-service     && ./mvnw spring-boot:run    # 8085

# Python
cd services/ia-service && .venv/bin/python -m app.main        # 8084

# Go
cd services/iot-service && go run ./cmd                       # 8083
```

> `ADMIN_PASSWORD` y otros secretos pueden contener `$`: no cargues `.env` con `source` en el
> shell (lo expande). Usa la opción de archivo de entorno del IDE.

### 3. Verificar

```bash
curl -s localhost:8888/actuator/health
curl -s localhost:8761/actuator/health
curl -s localhost:8080/actuator/health
curl -s localhost:8081/actuator/health
curl -s localhost:8082/actuator/health
curl -s localhost:8085/actuator/health
curl -s localhost:8083/health
curl -s localhost:8084/api/health
curl -s localhost:8080/api/parking-service/v1/public/parkings   # disponibilidad pública
```

Documentación Swagger de cada servicio Java: `http://localhost:<puerto>/swagger-ui.html`.
ia-service: `http://localhost:8084/api/docs`.

---

## Pruebas

| Servicio | Comando |
|---|---|
| auth, parking, payment | `./mvnw test` (el test `contextLoads` necesita config-server y la BD en marcha) |
| iot-service | `go build ./... && go test ./...` |
| ia-service | `.venv/bin/python -m pytest` (no carga los modelos ni usa MongoDB) |

---

## Estructura

```text
backend/
├── docker-compose.yml        Bases de datos, Redis, Mosquitto y MinIO
├── .env.example              Plantilla de secretos (copiar a .env)
├── docker-init/              postgres, mysql, mongodb, oracle, sqlserver, mosquitto
├── README-DOCKER.md
├── infrastructure/
│   ├── config-server/
│   ├── eureka-server/
│   └── api-gateway/
└── services/
    ├── auth-service/         com.winnersystems.smartparking.auth
    ├── parking-service/      com.winnersystems.smartparking.parking
    ├── payment-service/      com.winnersystems.smartparking.payment
    ├── iot-service/          Go
    └── ia-service/           Python
```

Capas de los servicios hexagonales:

```text
domain/          modelo puro (sin anotaciones de framework) y excepciones
application/     dto, port/input (casos de uso), port/output (puertos), service
infrastructure/  adapter/input/rest, adapter/output/persistence, config (seguridad, Feign)
```

---

**Winner Systems Corporation S.A.C.** · Huaraz, Áncash, Perú · [www.winner-systems.com](https://www.winner-systems.com)
