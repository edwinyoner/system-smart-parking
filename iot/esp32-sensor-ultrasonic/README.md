# Sensor de un espacio

**Placa:** ESP32-C6-DevKitC-1 (WiFi 6 y Bluetooth 5) + sensor ultrasónico **HC-SR04**.

## Qué hace

Mide la distancia cada segundo (mediana de 5 lecturas, que descarta rebotes) y la publica por
MQTT en `smartparking/sensors/{deviceId}/reading`:

```json
{"distance_cm": 42.5}
```

**El sensor solo mide**: iot-service decide si el espacio está ocupado (hasta 80 cm,
`SENSOR_OCCUPIED_MAX_CM`), confirma el cambio si dura 5 s (antirrebote: alguien que pasa no cambia
el estado) y avisa a parking-service, que actualiza el espacio y los paneles.

Como el servidor necesita más de una lectura para confirmar un cambio, el sensor publica cada 2 s
durante 20 s tras un cambio aparente, y cada 30 s en reposo. Una lectura sin eco no se publica.

## Conexiones

| HC-SR04 | ESP32-C6 |
|---|---|
| VCC | 5V |
| GND | GND |
| TRIG | GPIO 4 |
| ECHO | GPIO 5, **con divisor de voltaje** (1 kOhm en serie y 2 kOhm a GND) |

Montaje: arriba del espacio apuntando al piso (vacío = distancia al piso; ocupado = distancia al
techo del vehículo) o al frente, a la altura del parachoques. Ajusta `SENSOR_OCCUPIED_MAX_CM` en
iot-service y `LOCAL_OCCUPIED_MAX_CM` aquí con el mismo valor.

## Configurar y grabar

```bash
cd iot/esp32-sensor-ultrasonic
cp include/secrets.example.h include/secrets.h   # WiFi, broker, deviceId y clave MQTT
pio run -t upload && pio device monitor
```

La clave es la de la línea `deviceId:clave` de `backend/docker-init/mosquitto/devices.txt`.
El mismo `deviceId` va como **sensor** del espacio en el panel (editar espacio): así
parking-service sabe qué espacio mide.
