# Database

Cada microservicio tiene **su propia base de datos** (*database per service*), con motores
distintos a propósito:

| Servicio | Motor | Base |
|---|---|---|
| auth-service | PostgreSQL 18 | `auth_service_db` |
| parking-service | Oracle 21c XE | PDB `XEPDB1`, usuario `parking_user` |
| payment-service | SQL Server 2022 | `payment_service_db` |
| iot-service | MySQL 8.0 + Redis 7 | `iot_service_db` |
| ia-service | MongoDB | `ia_service_db` |

## Dónde están los scripts

- **Creación de bases y usuarios:** `backend/docker-init/<motor>/` (se ejecutan al crear cada
  contenedor). Ver [`backend/README-DOCKER.md`](../backend/README-DOCKER.md).
- **Tablas de auth, parking y payment:** las genera Hibernate al arrancar (`ddl-auto:
  create-drop` en desarrollo) y los seeders cargan los datos iniciales.
- **Tablas de iot-service:** `backend/services/iot-service/scripts/init.sql` y `migrations/`.
- **Índices de ia-service:** los crea el servicio al arrancar (`captureId` único y TTL).

Esta carpeta solo guarda scripts sueltos de prueba (`test_01_postgresql.sql`, `Oracle/`).
