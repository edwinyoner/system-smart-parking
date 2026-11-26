# Cloud - Despliegue

**Todavía no hay archivos de despliegue.** En desarrollo, los datos corren en Docker Compose
([`backend/README-DOCKER.md`](../backend/README-DOCKER.md)) y los microservicios desde el IDE.

## Plan (decidido para la tesis)

| Pieza | En Google Cloud |
|---|---|
| Microservicios (config, eureka, gateway, auth, parking, payment, iot, ia) | Máquinas virtuales de Compute Engine |
| Bases de datos (PostgreSQL, Oracle, SQL Server, MySQL, MongoDB, Redis) | Máquinas virtuales de Compute Engine (Cloud SQL sale más caro) |
| Broker MQTT | Mosquitto en una VM, con TLS (8883) |
| Fotos de las cámaras | Google Cloud Storage: bucket privado y URL firmada (`STORAGE_PROVIDER=gcs` en iot-service; reemplaza a MinIO) |

Reglas:

- Solo el puerto 8080 (api-gateway) y el del broker MQTT quedan expuestos a internet; el resto,
  en la red interna.
- Los secretos se cargan en las VM como variables de entorno o archivos `.env` fuera del
  repositorio.
- Se hará paso por paso, después de cerrar los módulos.
