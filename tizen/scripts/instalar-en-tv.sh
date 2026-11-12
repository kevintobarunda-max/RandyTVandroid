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

# El instalador de prueba Samsung que sí funcionó incluía icon.png.
# Creamos un PNG ligero localmente sin añadir dependencias de imagen.
python3 - "$BUILD/icon.png" <<'PY'
import struct, sys, zlib
width = height = 256
pixels = []
for y in range(height):
    row = bytearray()
    for x in range(width):
        # Icono sencillo color menta, con letra R en estilo de bloques.
        base = (14, 34, 44)
        edge = (116, 231, 188) if x < 10 or x >= 246 or y < 10 or y >= 246 else base
        vert = 61 <= x < 87 and 54 <= y < 205
        top = 61 <= x < 165 and 54 <= y < 79
        mid = 61 <= x < 165 and 119 <= y < 145
        round_side = 145 <= x < 172 and 71 <= y < 129
        leg = 116 <= x < 148 and 142 <= y < 184
        lower_leg = 140 <= x < 174 and 177 <= y < 205
        color = (116, 231, 188) if vert or top or mid or round_side or leg or lower_leg else edge
        row.extend(color)
    pixels.append(b'\0' + bytes(row))
def chunk(tag, data):
    return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)
png = b'\x89PNG\r\n\x1a\n'
png += chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0))
png += chunk(b'IDAT', zlib.compress(b''.join(pixels), 9))
png += chunk(b'IEND', b'')
with open(sys.argv[1], 'wb') as f:
    f.write(png)
PY

# Comprobar el manifiesto antes de firmar o transferir el paquete.
python3 - "$BUILD/config.xml" "$BUILD/icon.png" <<'PY'
import re, sys, xml.etree.ElementTree as ET
manifest, icon = sys.argv[1:3]
root = ET.parse(manifest).getroot()
ns = {'t':'http://tizen.org/ns/widgets', 'w':'http://www.w3.org/ns/widgets'}
app = root.find('t:application', ns)
name = root.find('w:name', ns)
screen = root.find('w:feature', ns)
icon_elem = root.find('w:icon', ns)
if app is None or not re.fullmatch(r'[A-Za-z0-9]{10}', app.get('package','')):
    sys.exit('ERROR: ID de paquete inválido en config.xml')
if not app.get('id','').startswith(app.get('package','') + '.'):
    sys.exit('ERROR: ID de aplicación y paquete no coinciden')
if name is None or not (name.text or '').strip() or screen is None or icon_elem is None:
    sys.exit('ERROR: faltan nombre, icono o feature de pantalla en config.xml')
if icon_elem.get('src') != 'icon.png':
    sys.exit('ERROR: config.xml no apunta a icon.png')
with open(icon, 'rb') as f:
    if f.read(8) != b'\x89PNG\r\n\x1a\n':
        sys.exit('ERROR: icon.png inválido')
print('✓ Manifiesto TV e icono verificados:', app.get('id'))
PY

if [[ ! -s "$BUILD/config.xml" || ! -s "$BUILD/js/local-config.js" || ! -s "$BUILD/icon.png" ]]; then
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

# Confirmar el contenido real del ZIP en vez de grep -q con pipefail.
# Las firmas deben estar en la raíz del .wgt.
verificar_firmas() {
  python3 - "$1" <<'PY'
import os, sys, zipfile
wgt = sys.argv[1]
try:
    with zipfile.ZipFile(wgt) as z:
        names = set(z.namelist())
        for signature in ("author-signature.xml", "signature1.xml"):
            print(("✓" if signature in names else "✗") + " " + signature)
        if "author-signature.xml" not in names or "signature1.xml" not in names:
            print("Contenido del paquete (solo nombres de archivos; sin credenciales):")
            for name in sorted(names):
                print(" - " + name)
            sys.exit(1)
except (OSError, zipfile.BadZipFile) as exc:
    print("ERROR: archivo .wgt inválido:", exc)
    sys.exit(2)
PY
}

echo "Comprobando las firmas reales dentro de $(basename "$WGT")..."
if ! verificar_firmas "$WGT"; then
  echo "El .wgt no tiene ambas firmas en la raíz."
  echo "Intentando volver a firmar el archivo .wgt mediante Tizen CLI..."
  # La documentación de Tizen permite re-firmar un .wgt ya empaquetado.
  if ! tizen package -t wgt -s "$PROFILE" -- "$WGT"; then
    echo "ERROR: falló la firma de este .wgt con el perfil $PROFILE."
    exit 1
  fi
  # Algunos SDK generan un segundo WGT; localizar la ruta de salida.
  if [[ ! -f "$WGT" ]]; then
    echo "ERROR: no encuentro el archivo .wgt después de firmar."
    exit 1
  fi
  echo "Verificando nuevamente el contenido real..."
  if ! verificar_firmas "$WGT"; then
    echo "ERROR: el SDK sigue generando un paquete sin firmas válidas."
    echo "El perfil se ha usado antes correctamente con RandyPrueba."
    echo "No es necesario crear otro certificado. Comparar ahora config.xml."
    exit 1
  fi
fi
echo "✓ Archivo .wgt firmado y comprobado."

# Validar el archivo ZIP real y reportar tamaño antes de enviarlo al TV.
echo "Validando integridad del paquete..."
python3 - "$WGT" <<'PY'
import os, sys, zipfile, xml.etree.ElementTree as ET
p=sys.argv[1]
size=os.stat(p).st_size
print("Tamaño: %.2f MB (%d bytes)"%(size/1048576,size))
try:
    with zipfile.ZipFile(p) as z:
        bad=z.testzip()
        if bad:
            sys.exit("ERROR: paquete ZIP dañado; primer archivo corrupto: "+bad)
        if "config.xml" not in z.namelist():
            sys.exit("ERROR: config.xml ausente dentro del WGT")
        root=ET.fromstring(z.read("config.xml"))
        app=root.find("{http://tizen.org/ns/widgets}application")
        if app is None or not app.get("id"):
            sys.exit("ERROR: manifiesto sin ID de aplicación")
        print("Aplicación: "+app.get("id"))
except (OSError,zipfile.BadZipFile,ET.ParseError) as err:
    sys.exit("ERROR: no se puede validar WGT: "+str(err))
print("✓ ZIP completo y manifiesto legible.")
PY

echo "3/4 Conectando a Samsung en $TV_IP"
sdb connect "$TV_IP"
echo "Dispositivos conectados:"
sdb devices
SERIAL="${TV_IP}:26101"

echo "4/4 Instalando $(basename "$WGT") en $SERIAL"
INSTALL_LOG="$(mktemp "${TMPDIR:-/tmp}/randy-install.XXXXXX")"
PUSH_LOG=""
DIRECT_LOG=""
cleanup_install_logs() {
  rm -f "$INSTALL_LOG"
  [[ -z "$PUSH_LOG" ]] || rm -f "$PUSH_LOG"
  [[ -z "$DIRECT_LOG" ]] || rm -f "$DIRECT_LOG"
}
trap cleanup_install_logs EXIT
set +e
tizen install -s "$SERIAL" -n "$(basename "$WGT")" -- "$(dirname "$WGT")" 2>&1 | tee "$INSTALL_LOG"
INSTALL_RC=${PIPESTATUS[0]}
set -e

# Algunos SDK imprimen "Failed to install" incluso después de "install completed".
if grep -Eq 'app_id\[.*\] install completed|Tizen application is successfully installed\.' "$INSTALL_LOG"; then
  echo "✓ El televisor confirmó que Randy TV fue instalado."
  exit 0
fi

# La transferencia ocurre antes de verificar el manifiesto o certificados.
if grep -Eiq 'Can not transfer .*package|problem to transfer package|Transfer.*fail' "$INSTALL_LOG"; then
  echo "La CLI no pudo TRANSFERIR el archivo. Intentando SDB directo..."
  REMOTE_NAME="RandyTV-$(date +%Y%m%d%H%M%S)-$$.wgt"
  REMOTE_PATH="/home/owner/share/tmp/sdk_tools/$REMOTE_NAME"
  PUSH_LOG="$(mktemp "${TMPDIR:-/tmp}/randy-sdb-push.XXXXXX")"
  set +e
  sdb -s "$SERIAL" push "$WGT" "$REMOTE_PATH" 2>&1 | tee "$PUSH_LOG"
  PUSH_RC=${PIPESTATUS[0]}
  set -e
  if [[ "$PUSH_RC" -ne 0 ]] || grep -Eiq 'fail|error|closed|permission denied|no space|not found' "$PUSH_LOG" || ! grep -Eiq 'pushed|transferred|100%' "$PUSH_LOG"; then
    echo "✗ La transferencia directa SDB también falló."
    echo "Verifica conexión y estado del televisor: sdb devices"
    echo "Verifica espacio y tamaño del archivo; no es un problema confirmado de certificado."
    exit 1
  fi
  echo "✓ SDB transfirió el archivo. Iniciando instalación directa en Samsung..."
  DIRECT_LOG="$(mktemp "${TMPDIR:-/tmp}/randy-direct.XXXXXX")"
  set +e
  sdb -s "$SERIAL" shell 0 vd_appinstall RANDYTV123.RandyTV "$REMOTE_PATH" 2>&1 | tee "$DIRECT_LOG"
  DIRECT_RC=${PIPESTATUS[0]}
  set -e
  if grep -Eiq 'install failed|failed\[|error|invalid|closed|permission denied' "$DIRECT_LOG"; then
    echo "✗ El TV rechazó la instalación directa; revisa el error que acaba de mostrar."
    exit 1
  fi
  if grep -Eq 'install completed|successfully installed|installed' "$DIRECT_LOG"; then
    echo "✓ El TV informó instalación correcta de Randy TV."
  else
    echo "La orden directa no confirmó expresamente el resultado."
    echo "Comprueba en Apps si aparece RandyTV; no lo consideramos instalado todavía."
    exit 1
  fi
  exit 0
fi

if [[ "$INSTALL_RC" -ne 0 ]] || grep -Eiq 'Failed to install Tizen application|install failed' "$INSTALL_LOG"; then
  echo "✗ El televisor rechazó instalar Randy TV. Revisa el resultado detallado anterior."
  echo "La prueba mínima RandyPrueba sí se instaló correctamente con RANDYTVPRO."
  exit 1
fi
echo "La instalación no informó éxito o fallo de forma concluyente; revisa Apps en el televisor."
