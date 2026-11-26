#!/bin/bash
# ==============================================================================
# Arranque de SQL Server 2022 para payment-service (un solo contenedor)
#
# La imagen oficial de Microsoft no ejecuta scripts al iniciar (PostgreSQL y
# MySQL sí, con docker-entrypoint-initdb.d). Este script lo hace:
#   1. Inicia el motor (sqlservr) en segundo plano.
#   2. Espera a que acepte conexiones.
#   3. Ejecuta 01-init.sql: crea payment_service_db y el usuario del servicio.
#   4. Se queda esperando al motor, que es el proceso que mantiene vivo el contenedor.
#
# Si el paso 3 falla, el motor NO se detiene: el error queda en el log y el
# healthcheck (que entra con el usuario del servicio) marca el contenedor como
# unhealthy. Así un fallo del script se ve en Docker Desktop sin tumbar la base.
# ==============================================================================
set -u

SQLCMD=/opt/mssql-tools18/bin/sqlcmd
INIT_SQL=/docker-init/01-init.sql

log() {
  echo "[sqlserver-init] $*"
}

# 1. Motor en segundo plano
/opt/mssql/bin/sqlservr &
SQL_PID=$!

# docker stop envía SIGTERM a este script (PID 1): se reenvía al motor para que cierre limpio
trap 'log "Deteniendo SQL Server..."; kill -TERM "$SQL_PID" 2>/dev/null; wait "$SQL_PID"; exit $?' TERM INT

# 2. Esperar a que acepte conexiones (hasta 2 minutos)
ready=false
for _ in $(seq 1 60); do
  if ! kill -0 "$SQL_PID" 2>/dev/null; then
    log "ERROR: el motor se detuvo al arrancar (revisa MSSQL_SA_PASSWORD y la memoria de Docker)"
    wait "$SQL_PID"
    exit 1
  fi
  if "$SQLCMD" -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -b -Q "SELECT 1" >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 2
done

# 3. Base y usuario del servicio (01-init.sql es idempotente: se ejecuta en cada arranque)
if [ "$ready" = true ]; then
  log "Motor listo. Ejecutando $INIT_SQL"
  if "$SQLCMD" -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -b -i "$INIT_SQL" \
       -v APP_USER="$MSSQL_USER" APP_PASSWORD="$MSSQL_PASSWORD"; then
    log "Listo: base payment_service_db y usuario $MSSQL_USER"
  else
    log "ERROR: falló $INIT_SQL. El motor sigue en marcha; el healthcheck quedará unhealthy"
  fi
else
  log "ERROR: el motor no respondió en 2 minutos; no se ejecutó $INIT_SQL"
fi

# 4. El contenedor vive mientras viva el motor
wait "$SQL_PID"
