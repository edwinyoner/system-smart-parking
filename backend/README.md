<div align="center">
<h1>
Winner Systems - Smart Parking Platform
</h1>

![Java](https://img.shields.io/badge/Java-25-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.7-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)
![Spring Cloud](https://img.shields.io/badge/Spring_Cloud-4.3.0-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Go](https://img.shields.io/badge/Go-1.23-00ADD8?style=for-the-badge&logo=go&logoColor=white)
![Python](https://img.shields.io/badge/Python-3.12-3776AB?style=for-the-badge&logo=python&logoColor=white)
![C](https://img.shields.io/badge/C-A8B9CC?style=for-the-badge&logo=c&logoColor=white)
![C++](https://img.shields.io/badge/C++-00599C?style=for-the-badge&logo=cplusplus&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-3.9-C71A36?style=for-the-badge&logo=apache-maven&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=for-the-badge&logo=mysql&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![MongoDB](https://img.shields.io/badge/MongoDB-7.0-47A248?style=for-the-badge&logo=mongodb&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7.2-DC382D?style=for-the-badge&logo=redis&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-24.0-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![Kubernetes](https://img.shields.io/badge/Kubernetes-1.28-326CE5?style=for-the-badge&logo=kubernetes&logoColor=white)

![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)
</div>

---

## 📋 Tabla de Contenidos

- [Acerca del Proyecto](#acerca-del-proyecto)
- [Arquitectura](#arquitectura)
- [Tecnologías](#tecnologías)
- [Requisitos Previos](#requisitos-previos)
- [Instalación](#instalación)
- [Microservicios](#microservicios)
- [Configuración](#configuración)
- [Ejecución](#ejecución)
- [Testing](#testing)
- [Despliegue](#despliegue)
- [API Documentation](#api-documentation)
- [Licencia](#licencia)
- [Contacto](#contacto)

---

## 📖 Acerca del Proyecto

**Smart Parking Platform - Backend** es la capa de servicios backend desarrollada por [Winner Systems Corporation S.A.C.](https://www.winner-systems.com) utilizando una arquitectura de microservicios moderna y escalable.

### Objetivo

Proporcionar una infraestructura robusta, escalable y mantenible para la gestión integral de estacionamientos inteligentes, aplicando tecnologías de vanguardia como IoT, Inteligencia Artificial y Cloud Computing.

### Características Principales

- ✅ **Arquitectura de Microservicios** con Spring Cloud
- ✅ **Service Discovery** con Eureka Server
- ✅ **Configuración Centralizada** con Config Server
- ✅ **API Gateway** para enrutamiento inteligente
- ✅ **Arquitectura Hexagonal** en cada microservicio
- ✅ **Servicios IoT** en Go para alto rendimiento
- ✅ **Procesamiento AI/ML** con Python
- ✅ **Componentes críticos** en C/C++ para máxima eficiencia
- ✅ **Contenedorización** con Docker
- ✅ **Orquestación** con Kubernetes

---

## 🏗️ Arquitectura

```
┌─────────────────────────────────────────────────────────────────┐
│                         API Gateway                              │
│                        (Port: 8080)                              │
│              Enrutamiento, Autenticación, CORS                   │
└────────────┬────────────────────────────────────────────────────┘
             │
             ├──────────────────┬─────────────────┬────────────────
             │                  │                 │
    ┌────────▼────────┐ ┌──────▼──────┐  ┌──────▼──────────┐
    │ Eureka Server   │ │Config Server│  │ Auth Service    │
    │  (Port: 8761)   │ │(Port: 8888) │  │  (Port: 8081)   │
    │ Service Registry│ │Central Config│  │  Spring Boot    │
    └─────────────────┘ └─────────────┘  └─────────────────┘
             │
             │  Service Discovery & Load Balancing
             │
    ┌────────┴──────────────────────────────────────────────────┐
    │                                                            │
┌───▼────────────┐  ┌──────────────┐  ┌──────────────────────┐
│ User Service   │  │ Parking      │  │ Payment Service      │
│ (Port: 8082)   │  │ Service      │  │ (Port: 8084)         │
│ Spring Boot    │  │ (Port: 8083) │  │ Spring Boot          │
│ PostgreSQL     │  │ Spring Boot  │  │ MySQL                │
└────────────────┘  │ MySQL        │  └──────────────────────┘
                    └──────────────┘
┌────────────────┐  ┌──────────────┐  ┌──────────────────────┐
│ IoT Service    │  │ Notification │  │ Analytics Service    │
│ (Port: 8085)   │  │ Service      │  │ (Port: 8087)         │
│ Go             │  │ (Port: 8086) │  │ Python               │
│ MongoDB        │  │ Spring Boot  │  │ MongoDB              │
└────────────────┘  │ Redis        │  └──────────────────────┘
                    └──────────────┘
┌────────────────┐  ┌──────────────┐
│ ML/AI Service  │  │ Sensor       │
│ (Port: 8088)   │  │ Processing   │
│ Python         │  │ C/C++        │
│ TensorFlow     │  │ High Perf.   │
└────────────────┘  └──────────────┘
```

---

## 🚀 Tecnologías

### Backend Core

| Tecnología | Versión | Propósito |
|-----------|---------|-----------|
| **Java** | 25 | Microservicios de negocio |
| **Spring Boot** | 3.5.7 | Framework principal |
| **Spring Cloud** | 4.3.0 | Microservicios cloud-native |
| **Go** | 1.23+ | Servicios IoT de alto rendimiento |
| **Python** | 3.12+ | Servicios de IA/ML |
| **C/C++** | - | Procesamiento de sensores crítico |

### Infraestructura

| Servicio | Puerto | Tecnología | Estado |
|----------|--------|-----------|--------|
| **Config Server** | 8888 | Spring Cloud Config | ✅ Implementado |
| **Eureka Server** | 8761 | Netflix Eureka | ✅ Implementado |
| **API Gateway** | 8080 | Spring Cloud Gateway | ✅ Implementado |
| **Auth Service** | 8081 | Spring Boot + JWT | 🔄 En desarrollo |
| **User Service** | 8082 | Spring Boot | 📋 Planificado |

### Bases de Datos

- **PostgreSQL** 16 - Datos transaccionales (usuarios, roles)
- **MySQL** 8.0 - Datos de estacionamiento y pagos
- **MongoDB** 7.0 - Datos IoT y analytics
- **Redis** 7.2 - Cache y sesiones

### DevOps & Cloud

- **Docker** - Contenedorización
- **Kubernetes** - Orquestación
- **GitHub Actions** - CI/CD
- **AWS/Azure** - Cloud deployment

---

## 📋 Requisitos Previos

### Java Development

```bash

# Java Development Kit (JDK)
Java 25 (Eclipse Temurin recomendado)
Descargar: https://adoptium.net/

# Maven
Maven 3.9+
Descargar: https://maven.apache.org/

# IDE (Opcional pero recomendado)
IntelliJ IDEA Community Edition
Descargar: https://www.jetbrains.com/idea/
```

### Go Development

```bash

# Go Programming Language
Go 1.23+
Descargar: https://go.dev/dl/
```

### Python Development

```bash

# Python
Python 3.12+
Descargar: https://www.python.org/

# pip (incluido con Python)
# virtualenv o venv para entornos virtuales
```

### C/C++ Development

```bash

# GCC Compiler
gcc/g++ 11+

# Linux/macOS: generalmente preinstalado
# Windows: MinGW o MSYS2
```

### Herramientas Adicionales

```bash

# Git
git 2.40+

# Docker (opcional, para contenedores)
Docker 24.0+
Docker Compose 2.20+

# Postman o Insomnia (para testing de APIs)
```

### Verificar Instalación

```bash

# Java y Maven
java --version          # openjdk 25
mvn --version           # Apache Maven 3.9.x

# Go
go version              # go version go1.23.x

# Python
python --version        # Python 3.12.x
pip --version           # pip 24.x

# C/C++
gcc --version           # gcc 11.x+
g++ --version           # g++ 11.x+

# Git
git --version           # git version 2.x.x

# Docker (opcional)
docker --version        # Docker version 24.x
docker-compose --version # Docker Compose version 2.x
```

---

## 💻 Instalación

### 1. Clonar el Repositorio

```bash

git clone https://github.com/edwinyoner/system-smart-parking.git
cd system-smart-parking/backend
```

### 2. Estructura del Proyecto

```
backend/
├── infrastructure/              # Servicios de infraestructura
│   ├── config-server/          # Configuración centralizada (Puerto 8888)
│   ├── eureka-server/          # Service discovery (Puerto 8761)
│   └── api-gateway/            # API Gateway (Puerto 8080)
├── services/                    # Microservicios de negocio
│   ├── auth-service/           # Autenticación (Spring Boot - Java)
│   ├── user-service/           # Usuarios (Spring Boot - Java)
│   ├── parking-service/        # Estacionamiento (Spring Boot - Java)
│   ├── payment-service/        # Pagos (Spring Boot - Java)
│   ├── iot-service/            # IoT (Go)
│   ├── notification-service/   # Notificaciones (Spring Boot - Java)
│   ├── analytics-service/      # Analytics (Python)
│   ├── ml-service/             # Machine Learning (Python)
│   └── sensor-processing/      # Procesamiento de sensores (C/C++)
└── README.md                    # Este archivo
```

### 3. Configurar Variables de Entorno

```bash

# Copiar archivo de ejemplo
cp .env.example .env

# Editar con tus configuraciones
nano .env
```

---

## Ejecución

### Opción 1: Ejecución Manual (Desarrollo)

#### 1. Iniciar Config Server

```bash

cd infrastructure/config-server
mvn clean install
mvn spring-boot:run

# Servidor corriendo en: http://localhost:8888
```

#### 2. Iniciar Eureka Server

```bash

cd infrastructure/eureka-server
mvn clean install
mvn spring-boot:run

# Servidor corriendo en: http://localhost:8761
```

#### 3. Iniciar API Gateway

```bash

cd infrastructure/api-gateway
mvn clean install
mvn spring-boot:run

# Servidor corriendo en: http://localhost:8080
```

#### 4. Iniciar Microservicios de Negocio

```bash

# Ejemplo: Auth Service
cd services/auth-service
mvn clean install
mvn spring-boot:run
```

### Opción 2: Script de Inicio Automatizado

```bash

# Dar permisos de ejecución
chmod +x start-all.sh

# Ejecutar todos los servicios
./start-all.sh
```

### Opción 3: Docker Compose (Recomendado para Producción)

```bash

# Construir imágenes
docker-compose build

# Iniciar todos los servicios
docker-compose up -d

# Ver logs
docker-compose logs -f

# Detener servicios
docker-compose down
```

### Orden de Inicio Recomendado

1. **Config Server** (8888) - Primero siempre
2. **Eureka Server** (8761) - Segundo
3. **API Gateway** (8080) - Tercero
4. **Microservicios de negocio** - En cualquier orden

---

## Testing

### Tests Unitarios (Java/Spring Boot)

```bash

# Ejecutar tests de un microservicio
cd infrastructure/config-server
mvn test

# Ejecutar con coverage
mvn test jacoco:report

# Ver reporte en: target/site/jacoco/index.html
```

### Tests de Integración

```bash

# Ejecutar tests de integración
mvn verify
```

### Tests End-to-End

```bash

# Usando herramientas como Postman, Insomnia o scripts
cd tests/e2e
npm test
```

---

## API Documentation

### Swagger UI

Una vez que los servicios estén corriendo, accede a la documentación interactiva:

- **Config Server**: http://localhost:8888/swagger-ui.html
- **Eureka Server**: http://localhost:8761
- **API Gateway**: http://localhost:8080/swagger-ui.html
- **Auth Service**: http://localhost:8081/swagger-ui.html
- **User Service**: http://localhost:8082/swagger-ui.html

### Endpoints Principales

#### Config Server
```
GET http://localhost:8888/actuator/health
GET http://localhost:8888/{application}/{profile}
```

#### Eureka Server
```
GET http://localhost:8761/
GET http://localhost:8761/eureka/apps
```

#### API Gateway
```
GET http://localhost:8080/actuator/health
GET http://localhost:8080/actuator/gateway/routes
```

---

## 🐳 Despliegue

### Docker

```bash

# Build de imagen individual
docker build -t smart-parking/config-server:latest ./infrastructure/config-server

# Run de contenedor
docker run -p 8888:8888 smart-parking/config-server:latest
```

### Kubernetes

```bash

# Aplicar configuración
kubectl apply -f k8s/

# Ver pods
kubectl get pods

# Ver servicios
kubectl get services
```

### Cloud Deployment

- **AWS**: ECS/EKS
- **Azure**: AKS
- **Google Cloud**: GKE

---

## 🔧 Configuración

### application.yml

Los archivos de configuración centralizados están en:
```
infrastructure/config-server/src/main/resources/config/
├── application.yml           # Configuración global
├── api-gateway.yml          # Config para API Gateway
├── auth-service.yml         # Config para Auth Service
├── eureka-server.yml        # Config para Eureka Server
└── user-service.yml         # Config para User Service
```

### Variables de Entorno

```env
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=smart_parking
DB_USER=admin
DB_PASSWORD=secure_password

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# JWT
JWT_SECRET=your_jwt_secret_key
JWT_EXPIRATION=86400000

# Email
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USER=your_email@gmail.com
SMTP_PASSWORD=your_password
```

---

## Monitoreo

### Spring Boot Actuator

```bash

# Health check
curl http://localhost:8080/actuator/health

# Metrics
curl http://localhost:8080/actuator/metrics

# Info
curl http://localhost:8080/actuator/info
```

### Prometheus & Grafana

```bash

# Prometheus: http://localhost:9090
# Grafana: http://localhost:3000
```

---

## Desarrollo

### Crear Nuevo Microservicio (Java/Spring Boot)

```bash

# Usando Spring Initializr
curl https://start.spring.io/starter.zip \
  -d dependencies=web,data-jpa,actuator,eureka-client \
  -d name=nuevo-servicio \
  -d groupId=com.winnersystems.smartparking \
  -d artifactId=nuevo-servicio \
  -o nuevo-servicio.zip

unzip nuevo-servicio.zip -d services/nuevo-servicio
```

### Crear Nuevo Servicio (Go)

```bash

cd services
mkdir nuevo-servicio-go
cd nuevo-servicio-go
go mod init github.com/winnersystems/smartparking/nuevo-servicio
```

### Crear Nuevo Servicio (Python)

```bash

cd services
mkdir nuevo-servicio-python
cd nuevo-servicio-python
python -m venv venv
source venv/bin/activate  # Linux/macOS
# venv\Scripts\activate   # Windows
pip install flask
```

---

## 📄 Licencia

Distribuido bajo la Licencia MIT. Ver `LICENSE` para más información.

---

## 📞 Contacto

**Winner Systems Corporation S.A.C.**

- 🌐 Website: [www.winner-systems.com](https://www.winner-systems.com)
- 📧 Email: contacto@winner-systems.com
- 📱 WhatsApp: +51 931 741 355
- 📍 Dirección: Jangas, Huaraz, Áncash, Perú

**Desarrollador Principal:**
- 👨‍💻 Edwin Yoner
- 📧 edwinyoner@winner-systems.com
- 💼 LinkedIn: [linkedin.com/in/edwinyoner](https://linkedin.com/in/edwinyoner)
- 🐙 GitHub: [@edwinyoner](https://github.com/edwinyoner)

---

## Agradecimientos

- [Spring Framework](https://spring.io/) - Framework empresarial robusto
- [Netflix OSS](https://netflix.github.io/) - Herramientas de microservicios
- [Go Team](https://go.dev/) - Lenguaje eficiente y concurrente
- [Python Software Foundation](https://www.python.org/) - Lenguaje versátil para IA/ML
- [Docker](https://www.docker.com/) - Plataforma de contenedorización
- [Kubernetes](https://kubernetes.io/) - Orquestación de contenedores

---

<div align="center">

**Hecho en Perú 🇵🇪**

[![Winner Systems](https://img.shields.io/badge/Winner_Systems-0066CC?style=for-the-badge&logo=data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjQiIGhlaWdodD0iMjQiIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHBhdGggZD0iTTEyIDJMMiAyMkgyMkwxMiAyWiIgZmlsbD0id2hpdGUiLz4KPC9zdmc+&logoColor=white)](https://www.winner-systems.com)

**Construyendo el futuro de las ciudades inteligentes**

</div>