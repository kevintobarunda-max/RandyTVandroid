#!/bin/bash
# Randy TV — compilar, firmar e instalar en Samsung Tizen TV desde macOS.
# Uso: bash tizen/scripts/instalar-en-tv.sh IP_TV PERFIL_CERTIFICADO
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "\${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
PROJECT="$ROOT/tizen"
TV_IP="\${1:-}"
PROFILE="\${2:-}"

if [[ ! "$TV_IP" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Uso: bash tizen/scripts/instalar-en-tv.sh IP_DEL_TV PERFIL_CERTIFICADO"
  echo "Ej.: bash tizen/scripts/instalar-en-tv.sh 192.168.1.55 SamsungTV"
  exit 1
fi

if [[ -z "$PROFILE" ]]; then
  echo "Falta el nombre de perfil de certificado Samsung TV."
  echo "Crea uno para tu televisor desde Certificate Manager del SDK."
  exit 1
fi

# El SDK puede ser instalado por la extensión actual de Tizen para VS Code.
for sdk in "$HOME/tizen-studio/tools/ide/bin" "$HOME/tizen-sdk/tools/ide/bin" "/opt/tizen-studio/tools/ide/bin"; do
  if [[ -x "$sdk/tizen" ]]; then
    export PATH="$sdk:$PATH"
    break
  fi
done
for sdk in "$HOME/tizen-studio/tools" "$HOME/tizen-sdk/tools" "/opt/tizen-studio/tools"; do
  if [[ -x "$sdk/sdb" ]]; then
    export PATH="$sdk:$PATH"
    break
  fi
done

for cmd in tizen sdb python3; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Falta '$cmd'. Instala el Tizen SDK y Samsung TV Extension con VS Code."
    echo "Si ya lo instalaste en otra carpeta, agrega sus rutas al PATH."
    exit 1
  fi
done

if [[ ! -s "$PROJECT/js/local-config.js" ]]; then
  echo "Preparando datos de acceso existentes del proyecto Android..."
  python3 "$PROJECT/scripts/preparar-credenciales.py"
fi

echo "1/4 Compilando aplicación web Tizen..."
tizen build-web -- "$PROJECT"

echo "2/4 Empaquetando y firmando con perfil: $PROFILE"
tizen package -t wgt -s "$PROFILE" -- "$PROJECT/.buildResult"

WGT="$(find "$PROJECT/.buildResult" -maxdepth 1 -type f -name '*.wgt' -print -quit)"
if [[ -z "$WGT" ]]; then
  echo "No se generó un .wgt. Revisa el certificado y la salida del SDK."
  exit 1
fi

echo "3/4 Conectando a Samsung en $TV_IP"
sdb connect "$TV_IP"
echo "Dispositivos conectados:"
sdb devices
SERIAL="\${TV_IP}:26101"

echo "4/4 Instalando $(basename "$WGT") en $SERIAL"
tizen install -s "$SERIAL" -n "$(basename "$WGT")" -- "$(dirname "$WGT")"

echo "✓ Comando de instalación enviado. Revisa que termine sin errores."
echo "Busca Randy TV en Aplicaciones del televisor."
echo "NOTA: Para instalar, el TV requiere Developer Mode, IP del Mac permitida y certificado Samsung autorizado."
