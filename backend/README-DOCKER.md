# System Smart Parking - Contenedores de desarrollo

`backend/docker-compose.yml` levanta **solo la infraestructura de datos**: bases de datos,
caché, broker MQTT y almacenamiento de fotos. Los microservicios se ejecutan desde el IDE o la
terminal (no hay `Dockerfile` todavía).

Red: `smart-parking-network`. Todos los contenedores tienen healthcheck.

---

## Contenedores

| Servicio | Imagen | Contenedor | Puerto host → contenedor | Lo usa |
|---|---|---|---|---|
| postgres | `postgres:18-alpine` | `db-system-smart-parking-postgres` | **5433** → 5432 | auth-service (`auth_service_db`) |
| oracle | `container-registry.oracle.com/database/express:21.3.0-xe` | `db-system-smart-parking-oracle` | 1521, 5500 | parking-service (PDB `XEPDB1`, usuario `parking_user`) |
| sqlserver | `mcr.microsoft.com/mssql/server:2022-latest` | `db-system-smart-parking-sqlserver` | 1433 | payment-service (`payment_service_db`) |
| mysql | `mysql:8.0` | `db-system-smart-parking-mysql` | 3306 | iot-service (`iot_service_db`) |
| mongodb | `mongodb/mongodb-community-server:latest` | `db-system-smart-parking-mongodb` | 27017 | ia-service (`ia_service_db`) |
| redis | `redis:7-alpine` | `db-system-smart-parking-redis` | 6379 | iot-service (caché de disponibilidad, límite de peticiones) |
| mosquitto | `eclipse-mosquitto:2` | `smart-parking-mosquitto` | 1883 | iot-service, sensores HC-SR04 y paneles LED |
| minio | `minio/minio:latest` | `smart-parking-minio` | 9000 (API), 9001 (consola) | iot-service (fotos de las cámaras) |

Los scripts de `docker-init/<servicio>/` crean las bases y los usuarios la **primera vez** que
arranca cada contenedor (con el volumen vacío).

---

## Inicio rápido

```bash
cd backend
cp .env.example .env      # solo la primera vez; completa los secretos
docker compose up -d
docker compose ps         # todos deben quedar "healthy"
```

- **Oracle** tarda de 2 a 3 minutos la primera vez. Espera en los logs `DATABASE IS READY TO USE!`
  (`docker compose logs -f oracle`). Necesita al menos 8 GB de memoria en Docker Desktop.
- **SQL Server** usa su propio `docker-init/sqlserver/entrypoint.sh`: arranca el motor, ejecuta
  `01-init.sql` (base y usuario) y solo queda "healthy" cuando el usuario del servicio entra a su
  base.
- **Mosquitto** genera sus usuarios al arrancar (ver más abajo).

---

## Credenciales

Son **solo de desarrollo**. Nunca las uses en producción.

| Servicio | Dónde están |
|---|---|
| PostgreSQL, MySQL, MongoDB, Redis, MinIO | Fijas en `docker-compose.yml` (valores de desarrollo) |
| Oracle (`parking_user`) | `docker-init/oracle/01-init.sql` |
| SQL Server | `MSSQL_SA_PASSWORD`, `MSSQL_USER`, `MSSQL_PASSWORD` en `backend/.env` (la contraseña debe cumplir la política de complejidad de SQL Server) |
| Mosquitto | `MQTT_IOT_USERNAME` / `MQTT_IOT_PASSWORD` en `backend/.env` + `docker-init/mosquitto/devices.txt` |

`backend/.env` y `docker-init/mosquitto/devices.txt` están en `.gitignore`: **no se suben**.

---

## MQTT (Mosquitto)

Sin acceso anónimo. Cada dispositivo tiene su usuario y una ACL que limita sus temas:

| Usuario | Puede |
|---|---|
| iot-service (`MQTT_IOT_USERNAME`) | Leer y escribir en `smartparking/#` |
| Sensor HC-SR04 | Escribir solo en `smartparking/sensors/<su usuario>/reading` |
| Panel junto al parqueo | Leer `smartparking/parkings/+/availability` |
| Panel de avenida | Leer solo `smartparking/panels/<su usuario>/availability` |

Para registrar dispositivos:

```bash
cp docker-init/mosquitto/devices.example.txt docker-init/mosquitto/devices.txt
# una línea por dispositivo:  deviceId:clave   (el deviceId es el mismo de la tabla devices de iot-service)
docker compose restart mosquitto
```

En la nube se usará TLS (puerto 8883).

---

## Comandos útiles

```bash
docker compose ps                         # estado
docker compose logs -f oracle             # logs de un servicio
docker compose restart mysql              # reiniciar uno
docker compose down                       # detener (conserva los datos)
docker compose down -v                    # detener y BORRAR los volúmenes (se pierden los datos)

# Consolas
docker exec -it db-system-smart-parking-postgres psql -U postgres -d auth_service_db
docker exec -it db-system-smart-parking-mysql mysql -uroot -p iot_service_db
docker exec -it db-system-smart-parking-mongodb mongosh -u admin -p --authenticationDatabase admin
docker exec -it db-system-smart-parking-oracle sqlplus parking_user@XEPDB1
docker exec -it db-system-smart-parking-redis redis-cli -a <contraseña> PING
```

- Consola de MinIO: http://localhost:9001 (bucket de fotos definido en `S3_BUCKET_NAME` de
  `services/iot-service/.env`).
- Oracle Enterprise Manager Express: https://localhost:5500/em

---

## iot-service: esquema de MySQL

`docker-init/mysql/01-init.sql` crea la base. Las tablas de iot-service están en
`services/iot-service/scripts/init.sql`, y los cambios posteriores en
`services/iot-service/scripts/migrations/` (aplicarlos en orden sobre una base existente):

```bash
docker exec -i db-system-smart-parking-mysql mysql -uroot -p iot_service_db \
  < services/iot-service/scripts/migrations/002_sensor_readings.sql
```

---

## Problemas frecuentes

| Problema | Solución |
|---|---|
| Oracle no arranca o se reinicia | Dale más memoria a Docker Desktop. Si el volumen quedó dañado: `docker compose down`, `docker volume rm backend_oracle_data`, `docker compose up -d oracle` |
| auth-service no conecta a PostgreSQL | El puerto del host es **5433**, no 5432 |
| SQL Server queda "unhealthy" | Revisa que `MSSQL_PASSWORD` cumpla la complejidad y mira `docker compose logs sqlserver` |
| MongoDB: authentication failed | Usa `authSource=admin` en la URL de conexión |
| Mosquitto: "Falta MQTT_IOT_PASSWORD" | Falta la variable en `backend/.env` |
| Un script de `docker-init` no se ejecutó | Solo corren con el volumen vacío: bórralo (se pierden los datos) o ejecuta el script a mano |
