#!/bin/bash
# Randy TV — compilar, firmar e instalar en Samsung Tizen TV desde macOS.
# Uso: bash tizen/scripts/instalar-en-tv.sh IP_TV PERFIL_CERTIFICADO
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
PROJECT="$ROOT/tizen"
TV_IP="${1:-}"
PROFILE="${2:-}"

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

# La versión Samsung es HTML/CSS/JS estático.
# Algunas versiones del antiguo Web CLI fuerzan su propio Java moderno y fallan
# durante build-web con javax/xml/bind/JAXBException incluso con JAVA_HOME=8.
# Preparar los archivos web directamente evita esa fase sin omitir la firma.
echo "1/4 Preparando aplicación web estática para Tizen..."
BUILD="$PROJECT/.buildResult"
if [[ -d "$BUILD" ]]; then
  # Sólo limpiamos el directorio de salida generado por el propio instalador.
  find "$BUILD" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
fi
mkdir -p "$BUILD/js" "$BUILD/css"
cp "$PROJECT/config.xml" "$PROJECT/index.html" "$BUILD/"
cp "$PROJECT/css/app.css" "$BUILD/css/"
cp "$PROJECT/js/app.js" "$PROJECT/js/local-config.js" "$BUILD/js/"
if [[ ! -s "$BUILD/config.xml" || ! -s "$BUILD/js/local-config.js" ]]; then
  echo "Error: faltan archivos esenciales en el paquete de Randy TV."
  exit 1
fi

echo "2/4 Empaquetando y firmando con perfil: $PROFILE"
tizen package -t wgt -s "$PROFILE" -- "$PROJECT/.buildResult"

WGT="$(find "$PROJECT/.buildResult" -maxdepth 1 -type f -name '*.wgt' -print -quit)"
if [[ -z "$WGT" ]]; then
  echo "No se generó un .wgt. Revisa el certificado y la salida del SDK."
  exit 1
fi

# Validar firmas reales: algunos empaquetadores devuelven éxito aunque
# falten certificados y el .wgt luego sea rechazado por el televisor.
if ! command -v unzip >/dev/null 2>&1; then
  echo "Error: falta 'unzip' para verificar el contenido del paquete."
  exit 1
fi
echo "Comprobando firmas dentro de $(basename "$WGT")..."
if ! unzip -Z -1 "$WGT" | grep -Fxq "author-signature.xml"; then
  echo "ERROR DE FIRMA: falta author-signature.xml dentro del .wgt."
  echo "Tizen no encontró o no pudo usar el certificado de autor."
  echo "Revisa: tizen security-profiles list -n '$PROFILE'"
  echo "Y el archivo de perfiles: tizen cli-config -l"
  echo "Crea un certificado Samsung TV real y asociado al DUID del televisor."
  exit 1
fi
if ! unzip -Z -1 "$WGT" | grep -Fxq "signature1.xml"; then
  echo "ERROR DE FIRMA: falta signature1.xml dentro del .wgt."
  echo "Revisa el certificado distribuidor Samsung TV y el DUID del televisor."
  echo "Consulta: tizen security-profiles list -n '$PROFILE'"
  exit 1
fi
echo "✓ Firmas de autor y distribuidor presentes en el paquete."

echo "3/4 Conectando a Samsung en $TV_IP"
sdb connect "$TV_IP"
echo "Dispositivos conectados:"
sdb devices
SERIAL="${TV_IP}:26101"

echo "4/4 Instalando $(basename "$WGT") en $SERIAL"
tizen install -s "$SERIAL" -n "$(basename "$WGT")" -- "$(dirname "$WGT")"

echo "✓ Comando de instalación enviado. Revisa que termine sin errores."
echo "Busca Randy TV en Aplicaciones del televisor."
echo "NOTA: Para instalar, el TV requiere Developer Mode, IP del Mac permitida y certificado Samsung autorizado."
