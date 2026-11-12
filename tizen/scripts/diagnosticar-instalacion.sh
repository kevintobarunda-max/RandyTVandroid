#!/bin/bash
# Prueba de instalación limpia en Samsung Tizen: no incluye credenciales IPTV.
# Uso: bash tizen/scripts/diagnosticar-instalacion.sh 192.168.0.14 RANDYTVPRO
set -euo pipefail
IP="${1:-}"
PROFILE="${2:-}"
if [[ ! "$IP" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ || -z "$PROFILE" ]]; then
  echo "Uso: bash tizen/scripts/diagnosticar-instalacion.sh IP_TV PERFIL_CERTIFICADO"
  exit 2
fi
for d in "$HOME/tizen-studio/tools/ide/bin" "$HOME/tizen-sdk/tools/ide/bin"; do
  [[ ! -x "$d/tizen" ]] || export PATH="$d:$PATH"
done
for d in "$HOME/tizen-studio/tools" "$HOME/tizen-sdk/tools"; do
  [[ ! -x "$d/sdb" ]] || export PATH="$d:$PATH"
done
command -v tizen >/dev/null || { echo "Falta Tizen CLI"; exit 1; }
command -v sdb >/dev/null || { echo "Falta SDB"; exit 1; }
command -v python3 >/dev/null || { echo "Falta Python 3"; exit 1; }
command -v unzip >/dev/null || { echo "Falta unzip"; exit 1; }
TMP="$(mktemp -d "${TMPDIR:-/tmp}/randy-tv-test.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT
cat > "$TMP/config.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<widget xmlns="http://www.w3.org/ns/widgets" xmlns:tizen="http://tizen.org/ns/widgets" id="http://randy.local/diagnostico" version="1.0.0" viewmodes="maximized">
  <tizen:application id="randytest1.Test" package="randytest1" required_version="2.3"/>
  <content src="index.html"/>
  <feature name="http://tizen.org/feature/screen.size.all"/>
  <icon src="icon.png"/>
  <name>RandyPrueba</name>
  <tizen:profile name="tv-samsung"/>
</widget>
XML
cat > "$TMP/index.html" <<'HTML'
<!doctype html><html><head><meta charset="utf-8"><title>Randy TV Prueba</title></head><body style="background:#101b28;color:#fff;font:40px Arial;text-align:center;margin-top:20%"><h1>Randy TV: instalación de prueba</h1><p>Certificado y aplicación de prueba instalados.</p></body></html>
HTML
# Ícono PNG sin dependencias ni secretos; 256 x 256
python3 - "$TMP/icon.png" <<'PY'
import struct, sys, zlib
width=height=256
pixels=b''.join(b'\0'+b'\x11\x62\x55'*width for _ in range(height))
def chunk(tag,data):
    return struct.pack(">I",len(data))+tag+data+struct.pack(">I",zlib.crc32(tag+data)&0xffffffff)
payload=b'\x89PNG\r\n\x1a\n'
payload+=chunk(b'IHDR',struct.pack(">IIBBBBB",width,height,8,2,0,0,0))
payload+=chunk(b'IDAT',zlib.compress(pixels,9))
payload+=chunk(b'IEND',b'')
open(sys.argv[1],"wb").write(payload)
PY
echo "=== Empaquetando prueba mínima con perfil $PROFILE ==="
tizen package -t wgt -s "$PROFILE" -- "$TMP"
WGT="$(find "$TMP" -maxdepth 1 -type f -name '*.wgt' -print -quit)"
if [[ -z "$WGT" ]]; then echo "Error: no se creó paquete de prueba."; exit 1; fi
for sig in author-signature.xml signature1.xml; do
  unzip -Z -1 "$WGT" | grep -Fxq "$sig" || { echo "Falta $sig"; exit 1; }
done
echo "✓ Archivo de diagnóstico firmado con ambas firmas."
echo "=== Instalando prueba SIN credenciales en $IP ==="
sdb connect "$IP"
OUTPUT="$(mktemp "${TMPDIR:-/tmp}/randy-tv-log.XXXXXX")"
trap 'rm -rf "$TMP" "$OUTPUT"' EXIT
set +e
tizen install -s "$IP:26101" -n "$(basename "$WGT")" -- "$(dirname "$WGT")" 2>&1 | tee "$OUTPUT"
RC=${PIPESTATUS[0]}
set -e
if [[ "$RC" -ne 0 ]] || grep -iq 'Failed to install Tizen application\|install failed' "$OUTPUT"; then
  echo ""
  echo "✗ Ni siquiera se instaló la aplicación mínima."
  echo "Causas probables: certificado Samsung TV/DUID, permisos de instalación del dispositivo o perfil incompatible."
  echo "Comprueba que el distribuidor del perfil $PROFILE se creó como Samsung TV e incluye el DUID del equipo."
  exit 1
fi
if grep -Eiq 'successfully installed|installation completed|install completed|install success' "$OUTPUT"; then
  echo ""
  echo "✓ Se instaló la aplicación mínima en el TV."
  echo "El certificado y el mecanismo de instalación parecen funcionar."
  echo "El problema puede estar en config.xml o en el contenido de la app principal."
else
  echo "La orden no muestra confirmación inequívoca; comprueba en Apps si aparece RandyPrueba."
fi
