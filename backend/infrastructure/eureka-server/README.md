# eureka-server

Netflix Eureka (puerto **8761**): registro y descubrimiento de servicios. El gateway resuelve
las rutas `lb://AUTH-SERVICE`, `lb://PARKING-SERVICE` y `lb://PAYMENT-SERVICE` con él.

Se registran: api-gateway, auth-service, parking-service y payment-service. iot-service (Go) e
ia-service (Python) **no** se registran: el gateway llega a iot-service por una URI fija
(`IOT_SERVICE_URL`) e ia-service no es público.

## Requisito

config-server en marcha (puerto 8888). Su configuración está en `eureka-server.yml` del
config-server.

## Ejecutar

```bash
./mvnw spring-boot:run
```

## Verificar

- Panel: http://localhost:8761
- Salud: `curl -s localhost:8761/actuator/health`
- Aplicaciones registradas: `curl -s -H 'Accept: application/json' localhost:8761/eureka/apps`

> Tras reiniciar un servicio, el gateway puede responder 503 unos segundos hasta que Eureka
> actualiza el registro.
