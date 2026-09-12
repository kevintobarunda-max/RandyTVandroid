#!/bin/bash
# RandyTV — Arranca proxy + servidor web con un solo comando
# Uso: bash start.sh

MAC_IP=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo "172.20.10.14")

echo ""
echo "  RandyTV — Iniciando servicios..."
echo "  IP de tu Mac: $MAC_IP"
echo ""

# Actualizar la IP del proxy en index.html
sed -i '' "s|var SRV='http://[0-9.]*:9090'|var SRV='http://$MAC_IP:9090'|g" index.html
echo "  Proxy configurado en: http://$MAC_IP:9090"

# Arrancar proxy en background
node proxy.js &
PROXY_PID=$!
echo "  Proxy PID: $PROXY_PID"

# Arrancar servidor web en background  
python3 -m http.server 8000 &
WEB_PID=$!
echo "  Servidor web PID: $WEB_PID"

echo ""
echo "  ✓ Proxy activo:        http://$MAC_IP:9090"
echo "  ✓ Servidor web activo: http://$MAC_IP:8000"
echo ""
echo "  En el TV abre: http://$MAC_IP:8000"
echo ""
echo "  Presiona Ctrl+C para detener todo"
echo ""

# Esperar y limpiar al salir
trap "kill $PROXY_PID $WEB_PID 2>/dev/null; echo 'Servicios detenidos.'" EXIT
wait
