#!/bin/sh
# Genera usuarios y permisos del broker a partir de variables de entorno (backend/.env)
# y de devices.txt (opcional, no se versiona): una línea "deviceId:contraseña" por dispositivo.
set -eu

: "${MQTT_IOT_USERNAME:?Falta MQTT_IOT_USERNAME en backend/.env}"
: "${MQTT_IOT_PASSWORD:?Falta MQTT_IOT_PASSWORD en backend/.env}"

DATA=/mosquitto/data
PASSWD="$DATA/passwd"
ACL="$DATA/acl"
DEVICES=/mosquitto/init/devices.txt

rm -f "$PASSWD"
touch "$PASSWD"
chmod 0600 "$PASSWD"
mosquitto_passwd -b "$PASSWD" "$MQTT_IOT_USERNAME" "$MQTT_IOT_PASSWORD"

count=0
if [ -f "$DEVICES" ]; then
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in ''|'#'*) continue ;; esac
    device="${line%%:*}"
    secret="${line#*:}"
    if [ -z "$device" ] || [ "$device" = "$line" ] || [ -z "$secret" ]; then
      echo "devices.txt: línea inválida (formato deviceId:contraseña)" >&2
      continue
    fi
    mosquitto_passwd -b "$PASSWD" "$device" "$secret"
    count=$((count + 1))
  done < "$DEVICES"
fi

# iot-service lee y escribe todo; cada sensor publica solo en su tema (%u = su usuario = deviceId)
# y los paneles solo leen la disponibilidad (es información pública).
cat > "$ACL" <<ACL
user $MQTT_IOT_USERNAME
topic readwrite smartparking/#

pattern write smartparking/sensors/%u/reading
pattern read smartparking/parkings/+/availability
ACL
chmod 0600 "$ACL"
chown mosquitto:mosquitto "$PASSWD" "$ACL"

echo "Mosquitto: usuario de iot-service y $count dispositivo(s) configurados"
exec mosquitto -c /mosquitto/config/mosquitto.conf
