<div align="center">

# System Smart Parking

### Prototipo de Smart Parking con tecnologías de Internet of Things, Artificial Intelligence y Cloud Computing para una Smart City en Huaraz - Áncash, 2025

<br>

![Java](https://img.shields.io/badge/Java-25-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.7-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)
![Spring Cloud](https://img.shields.io/badge/Spring_Cloud-2025.0-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Go](https://img.shields.io/badge/Go-1.26-00ADD8?style=for-the-badge&logo=go&logoColor=white)
![Python](https://img.shields.io/badge/Python-3.12-3776AB?style=for-the-badge&logo=python&logoColor=white)
![FastAPI](https://img.shields.io/badge/FastAPI-0.115-009688?style=for-the-badge&logo=fastapi&logoColor=white)
![PyTorch](https://img.shields.io/badge/PyTorch-EE4C2C?style=for-the-badge&logo=pytorch&logoColor=white)
![Angular](https://img.shields.io/badge/Angular-20-DD0031?style=for-the-badge&logo=angular&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript-5.9-3178C6?style=for-the-badge&logo=typescript&logoColor=white)
![CoreUI](https://img.shields.io/badge/CoreUI-5.5-1F3B5C?style=for-the-badge&logo=coreui&logoColor=white)
![Flutter](https://img.shields.io/badge/Flutter-3.38-02569B?style=for-the-badge&logo=flutter&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![Oracle](https://img.shields.io/badge/Oracle-21c_XE-F80000?style=for-the-badge&logo=oracle&logoColor=white)
![SQL Server](https://img.shields.io/badge/SQL_Server-2022-CC2927?style=for-the-badge&logo=microsoftsqlserver&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=for-the-badge&logo=mysql&logoColor=white)
![MongoDB](https://img.shields.io/badge/MongoDB-47A248?style=for-the-badge&logo=mongodb&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=for-the-badge&logo=redis&logoColor=white)
![MQTT](https://img.shields.io/badge/MQTT-Mosquitto_2-660066?style=for-the-badge&logo=eclipsemosquitto&logoColor=white)
![ESP32](https://img.shields.io/badge/ESP32-000000?style=for-the-badge&logo=espressif&logoColor=white)
![Docker](https://img.shields.io/badge/Docker_Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![Google Cloud](https://img.shields.io/badge/Google_Cloud-4285F4?style=for-the-badge&logo=google-cloud&logoColor=white)

[![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)](LICENSE)
[![GitHub Issues](https://img.shields.io/github/issues/edwinyoner/system-smart-parking?style=for-the-badge)](https://github.com/edwinyoner/system-smart-parking/issues)

<br>

[Website](https://www.winner-systems.com) •
[Contacto](mailto:contacto@winner-systems.com) •
[WhatsApp](https://wa.me/51931741355)

</div>

---

> **Prototipo académico.** Proyecto de tesis de la Universidad Nacional Santiago Antúnez de
> Mayolo (UNASAM), alineado con el Plan Director de Ciudades Inteligentes (PCM, 2024) y las
> normas NTP-ISO 37120, 37122, 37106, 37123 y 37101:2024 de INACAL. **No** es un sistema oficial
> de la Municipalidad Provincial de Huaraz.

## Tabla de contenidos

- [Acerca del proyecto](#acerca-del-proyecto)
- [Cómo funciona](#cómo-funciona)
- [Arquitectura](#arquitectura)
- [Stack tecnológico](#stack-tecnológico)
- [Estructura del monorepo](#estructura-del-monorepo)
- [Inicio rápido](#inicio-rápido)
- [Estado de los módulos](#estado-de-los-módulos)
- [Convenciones](#convenciones)
- [Licencia](#licencia)
- [Equipo](#equipo)
- [Contacto](#contacto)

---

## Acerca del proyecto

System Smart Parking gestiona y muestra **en tiempo casi real** la disponibilidad de los
estacionamientos de Huaraz. Automatiza la entrada y salida de vehículos y la detección de
ocupación con IoT e inteligencia artificial, sobre una arquitectura de microservicios.

Sitios piloto: **Parqueo San Martín** y **Parqueo Las Banderas** (Huaraz).

### Problema

- Congestión en el centro de la ciudad y tiempo perdido buscando dónde estacionar.
- Ningún conductor sabe, antes de llegar, si un parqueo tiene espacios libres.
- Registro manual de entradas, salidas y cobros, sin datos para planificar la movilidad.

### Solución

1. **Cámaras con reconocimiento de placas** en la entrada y salida de los parqueos con barrera.
2. **Sensores ultrasónicos** y **cámara aérea** para saber qué espacio está ocupado.
3. **Disponibilidad pública** en paneles LED de la calle y en una app móvil sin login.
4. **Panel web** para administrar parqueos, zonas, espacios, turnos, tarifas, operadores y cobros.
5. **Pago antes de salir** con QR (Yape, Plin, banca móvil) o en efectivo.

---

## Cómo funciona

| Escenario | Hardware | IA | Resultado |
|---|---|---|---|
| **A. Parqueo con barrera** | ESP32-CAM en entrada y salida | YOLO26 + EasyOCR (lectura de placa) | Abre o cierra la estadía del vehículo |
| **B. Parqueo abierto (calle)** | Cámara aérea | YOLO26 con un polígono por espacio | Estado de cada espacio |
| **Sensores** | HC-SR04 + ESP32 (MQTT) | — | Confirma o corrige la ocupación de un espacio |
| **Paneles** | ESP32 + pantalla LED (MQTT o HTTP) | — | Muestran disponibles y ocupados |

Reglas principales:

- Una placa leída con confianza **≥ 0.85** se registra sola; con menos va a la cola **Por
  revisar** del panel, donde un operador la confirma o corrige.
- La disponibilidad se calcula desde el estado real de cada espacio, nunca desde contadores.
- Un parqueo solo atiende en los turnos que tiene configurados; la tarifa es la del turno vigente
  (por hora o fracción, con 5 minutos sin costo y 5 de tolerancia por defecto).
- La salida exige el pago previo. El operador desde el panel es el respaldo si el IoT falla.

---

## Arquitectura

```text
 Angular (panel) ──┐
 Flutter (app) ────┼──► api-gateway :8080 ──► auth-service      (PostgreSQL)
 ESP32-CAM ────────┘    única puerta        ├► parking-service   (Oracle)
                         pública             ├► payment-service   (SQL Server)
                                             └► iot-service       (MySQL + Redis)

 Sensores HC-SR04 ──MQTT──► Mosquitto ◄──MQTT── iot-service ──MQTT──► Paneles LED

 Tráfico interno (no pasa por el gateway):
   iot-service ──► ia-service (YOLO26 + EasyOCR, MongoDB)
   iot-service ──► parking-service /internal/**   (token de servicio)
   payment-service ◄──► parking-service /internal/charges/**  (token de servicio)
   parking-service ──► auth-service               (Feign)

 Infraestructura: config-server :8888 · eureka-server :8761 · MinIO (fotos; GCS en la nube)
```

- **Base de datos por servicio**, a propósito: cada microservicio tiene la suya.
- **Arquitectura hexagonal** en auth, parking y payment; **por capas** en iot-service (Go) e
  ia-service (Python).
- **Defensa en profundidad**: el gateway bloquea las rutas internas (`/internal/**`,
  `/service-token`) y cada servicio valida su JWT y sus permisos.
- Entre servicios se usa un **token de servicio** emitido por auth-service (client credentials).

Más detalle en [`backend/README.md`](backend/README.md).

---

## Stack tecnológico

| Componente | Tecnología | Arquitectura | Base de datos | Puerto |
|---|---|---|---|---|
| config-server | Spring Cloud Config | — | — | 8888 |
| eureka-server | Netflix Eureka | — | — | 8761 |
| api-gateway | Spring Cloud Gateway (WebFlux) | — | — | 8080 |
| auth-service | Java 25, Spring Boot 3.5.7 | Hexagonal | PostgreSQL 18 | 8081 |
| parking-service | Java 25, Spring Boot 3.5.7 | Hexagonal | Oracle 21c XE | 8082 |
| iot-service | Go 1.26, Gin, GORM, Paho MQTT | Por capas | MySQL 8.0 + Redis 7 | 8083 |
| ia-service | Python 3.12, FastAPI, PyTorch, YOLO26, EasyOCR | Por capas, sin estado | MongoDB | 8084 |
| payment-service | Java 25, Spring Boot 3.5.7 | Hexagonal | SQL Server 2022 | 8085 |
| Panel web | Angular 20, CoreUI 5.5, TypeScript 5.9 | Componentes standalone | — | 4200 |
| App móvil | Flutter 3.38, Riverpod 3, Dio, go_router, flutter_map | Feature-first | — | — |
| Mensajería IoT | MQTT (Eclipse Mosquitto 2) | — | — | 1883 |
| Archivos | MinIO (desarrollo) / Google Cloud Storage (nube) | — | — | 9000 |

---

## Estructura del monorepo

```text
system-smart-parking/
├── ai/                  Herramientas de IA fuera de producción (polígonos, futuro ajuste de modelos)
├── backend/
│   ├── docker-compose.yml   Bases de datos, Redis, Mosquitto y MinIO
│   ├── docker-init/         Scripts de inicialización de cada contenedor
│   ├── infrastructure/      config-server, eureka-server, api-gateway
│   └── services/            auth, parking, payment, iot (Go), ia (Python)
├── cloud/               Despliegue en Google Cloud (por hacer)
├── database/            Scripts sueltos de prueba
├── docs/                Documento de tesis (LaTeX)
├── frontend/            Panel administrativo Angular
├── iot/                 Firmware de los ESP32 (por hacer)
└── mobile/smart_parking App Flutter para conductores
```

---

## Inicio rápido

### Requisitos

Java 25, Maven 3.9 (o el `./mvnw` de cada servicio), Go 1.26, Python 3.12, Node.js 22,
Angular CLI 20, Flutter 3.38 y Docker Desktop con al menos 8 GB de memoria (por Oracle).

### 1. Bases de datos e infraestructura de datos

```bash
git clone https://github.com/edwinyoner/system-smart-parking.git
cd system-smart-parking/backend
cp .env.example .env          # completa los secretos (nunca subas .env)
docker compose up -d
docker compose ps             # espera a que todos estén "healthy" (Oracle tarda 2-3 min)
```

Detalle en [`backend/README-DOCKER.md`](backend/README-DOCKER.md).

### 2. Microservicios (en este orden)

Los microservicios se ejecutan desde el IDE o la terminal, no en Docker. Cada servicio Java debe
cargar las variables de `backend/.env`.

1. `config-server` → 2. `eureka-server` → 3. `api-gateway` →
4. `auth-service`, `parking-service`, `payment-service` → 5. `ia-service` → 6. `iot-service`

Comandos de cada uno en [`backend/README.md`](backend/README.md).

### 3. Clientes

```bash
# Panel web: http://localhost:4200
cd frontend && npm install && npm start

# App móvil (macOS o simulador de iOS)
cd mobile/smart_parking
flutter run -d macos --dart-define=API_BASE_URL=http://localhost:8080/api
```

---

## Estado de los módulos

| Módulo | Estado |
|---|---|
| Infraestructura (config, Eureka, gateway) | Completo |
| auth-service: usuarios, roles dinámicos, permisos, JWT, correo | Completo |
| parking-service: parqueos, zonas, espacios, turnos, tarifas, operadores, transacciones, ocupación, disponibilidad pública | Completo |
| payment-service: tipos de pago, cobros QR (pasarela simulada) y efectivo, devoluciones | Completo (comprobante interno pendiente) |
| iot-service: capturas de cámaras, sensores y paneles por MQTT, fotos en MinIO/GCS | Completo (visión aérea pendiente) |
| ia-service: reconocimiento de placas y ocupación aérea, registro mínimo en MongoDB | Completo (sin modelo propio de placas peruanas) |
| Panel web Angular | Casi completo |
| App móvil Flutter (versión 1, pública y sin login) | Completa |
| Firmware ESP32 | Por hacer |
| Despliegue en Google Cloud | Por hacer |

---

## Convenciones

- Comentarios de código y documentación en español.
- Commits con [Conventional Commits](https://www.conventionalcommits.org/):
  `feat`, `fix`, `docs`, `refactor`, `test`, `chore`.
- Paginación: `?page=0&size=20`; la respuesta trae `content`, `pageNumber`, `pageSize`,
  `totalElements` y `totalPages`.
- Ningún secreto en el repositorio: todo va en archivos `.env` ignorados por Git.

---

## Licencia

Distribuido bajo la Licencia MIT. Ver [`LICENSE`](LICENSE).

---

## Equipo

<div align="center">
<img src="https://github.com/edwinyoner.png" width="100" height="100" style="border-radius: 50%">

**Edwin Yoner Flores Rupay**
_Tesista · CEO & Founder - Winner Systems Corporation S.A.C._

[![LinkedIn](https://img.shields.io/badge/LinkedIn-0077B5?style=for-the-badge&logo=linkedin&logoColor=white)](https://linkedin.com/in/edwinyoner)
[![GitHub](https://img.shields.io/badge/GitHub-100000?style=for-the-badge&logo=github&logoColor=white)](https://github.com/edwinyoner)
[![Email](https://img.shields.io/badge/Email-D14836?style=for-the-badge&logo=gmail&logoColor=white)](mailto:edwinyoner@winner-systems.com)

</div>

**Asesor:** M.Sc. Franz Martín Villanueva Cochachin

**Institución:** Universidad Nacional Santiago Antúnez de Mayolo (UNASAM), Facultad de Ciencias,
Escuela de Ingeniería de Sistemas e Informática. Huaraz, Áncash, Perú.

---

## Contacto

**Winner Systems Corporation S.A.C.**

- Dirección: Jangas, Huaraz, Áncash, Perú
- Teléfono: +51 931 741 355
- Email: contacto@winner-systems.com
- Web: [www.winner-systems.com](https://www.winner-systems.com)

---

## Agradecimientos

- **UNASAM**, por el apoyo académico.
- **Municipalidad Provincial de Huaraz**, aliado institucional para los sitios piloto.
- Las comunidades de Spring, Go, FastAPI, Ultralytics, Angular, CoreUI y Flutter.

<div align="center">

**Hecho en Perú**

</div>
