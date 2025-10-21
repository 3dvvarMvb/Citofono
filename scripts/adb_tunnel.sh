#!/usr/bin/env bash
set -euo pipefail

BUS_PORT=${1:-5000}
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "[adb_tunnel] Configurando túnel ADB para puerto ${BUS_PORT}"

if ! command -v adb >/dev/null 2>&1; then
  echo "[adb_tunnel] ERROR: 'adb' no está en PATH. Instala Android Platform Tools o añade adb al PATH." >&2
  exit 2
fi

echo "[adb_tunnel] listando dispositivos ADB..."
DEVICES_RAW=$(adb devices -l)
echo "$DEVICES_RAW"

# Extraer device IDs que estén conectados (device|unauthorized|offline)
DEVICE_IDS=($(adb devices | tail -n +2 | awk '{print $1}' | sed '/^$/d'))

if [ ${#DEVICE_IDS[@]} -eq 0 ]; then
  echo "[adb_tunnel] No se detectaron dispositivos ADB. Asegúrate de tener el teléfono conectado y adb autorizado." >&2
  echo "[adb_tunnel] Si usas WiFi Pair, asegúrate de ejecutar: adb pair <ip>:<port> y luego adb connect <ip>:<port>" >&2
  exit 3
fi

# Si hay varios dispositivos, seleccionamos el primero
DEVICE_ID=${DEVICE_IDS[0]}
echo "[adb_tunnel] Usando dispositivo: $DEVICE_ID"

# Mostrar estado de reverses/forwards actuales
echo "[adb_tunnel] reverses actuales:"
adb reverse --list || true

# Intentar eliminar cualquier reverse existente para el puerto
echo "[adb_tunnel] Eliminando reverse existente en el dispositivo (si existe) para limpiar el estado..."
adb -s "$DEVICE_ID" reverse --remove tcp:${BUS_PORT} >/dev/null 2>&1 || true

# Intentar configurar adb reverse (desde dispositivo -> host). Esto permite que la app en el dispositivo acceda al servidor en el host usando localhost:PORT
echo "[adb_tunnel] Intentando 'adb reverse tcp:${BUS_PORT} tcp:${BUS_PORT}'..."
if adb -s "$DEVICE_ID" reverse tcp:${BUS_PORT} tcp:${BUS_PORT}; then
  echo "[adb_tunnel] OK: adb reverse configurado (dispositivo -> host). Ahora la app en el dispositivo puede usar '127.0.0.1:${BUS_PORT}' para conectar al host."
else
  echo "[adb_tunnel] WARNING: adb reverse falló. Intentaré 'adb forward' como fallback (host -> dispositivo)." >&2
  # Intentar adb forward como fallback (inusual para este caso, pero lo incluimos)
  adb -s "$DEVICE_ID" forward tcp:${BUS_PORT} tcp:${BUS_PORT} || {
    echo "[adb_tunnel] ERROR: No pude configurar reverse ni forward en el dispositivo." >&2
    exit 4
  }
  echo "[adb_tunnel] OK: adb forward configurado como fallback. Dependiendo del flujo esto puede o no servir."
fi

# Mostrar reverses/forwards finales
echo "[adb_tunnel] Estado final de reverses:"
adb -s "$DEVICE_ID" reverse --list || true

echo "[adb_tunnel] Estado final de forwards (en host):"
adb -s "$DEVICE_ID" forward --list || true

# Mostrar escucha en host (si netstat está disponible)
if command -v ss >/dev/null 2>&1; then
  echo "[adb_tunnel] Puertos escuchando (ss) en host para :${BUS_PORT}:"
  ss -tuln | grep ":${BUS_PORT} " || true
elif command -v netstat >/dev/null 2>&1; then
  echo "[adb_tunnel] Puertos escuchando (netstat) en host para :${BUS_PORT}:"
  netstat -tuln | grep ":${BUS_PORT} " || true
else
  echo "[adb_tunnel] (Info) 'ss' o 'netstat' no está disponible para listar puertos en host."
fi

echo "[adb_tunnel] Hecho. Si la app usa 127.0.0.1:${BUS_PORT} debería poder conectar al servidor en tu máquina."

