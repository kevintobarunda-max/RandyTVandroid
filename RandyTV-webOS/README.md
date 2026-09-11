# RandyTV — webOS TV (LG LK5700 / webOS 4)

App de IPTV Xtream Codes para LG Smart TV 2018 (webOS 4, Chromium 53).  
Incluye: TV en Vivo, Películas, navegación 100% por control remoto, canales 2MB y Simpsons 24h.

---

## ⚠️ PASO 0 — Sustituir hls.js por la versión real

El archivo `hls.min.js` incluido es un stub. **Antes de empaquetar**, descarga la versión real (ES5, compatible Chromium 53) en tu PC:

```
https://cdn.jsdelivr.net/npm/hls.js@0.12.4/dist/hls.min.js
```

Cópiala a `RandyTV-webOS/hls.min.js` (reemplaza el stub).

---

## ⚠️ PASO 1 — Activar Developer Mode en tu LG TV

1. En tu TV, abre el **Launcher** y busca la app **"Developer Mode"** (si no aparece, ve a `webostv.developer.lge.com` y descárgala desde el navegador del TV).
2. Activa el modo desarrollador y anota la **IP de tu TV**.
3. En la pantalla Developer Mode verás un **token de sesión** (Dev Mode App Key) — cópialo, lo necesitas para el paso 2.

---

## ⚠️ PASO 2 — Instalar ares-cli en tu PC (Windows/Mac/Linux)

```bash
npm install -g @webosose/ares-cli
```

---

## ⚠️ PASO 3 — Registrar tu TV en ares-cli

```bash
ares-setup-device
```

Responde las preguntas:
- **Device name**: `miLG`
- **IP**: la IP de tu TV (e.g. `192.168.1.50`)
- **Port**: `9922`
- **Username**: `prisoner`
- **SSH Key**: déjalo vacío por ahora

Luego conéctate una vez para aceptar la clave y autenticar con el token:

```bash
ares-novacom --device miLG --getkey
```

Ingresa el **Dev Mode App Key** cuando lo pida.

---

## ⚠️ PASO 4 — Empaquetar la app

Desde el directorio que contiene `RandyTV-webOS/`:

```bash
ares-package RandyTV-webOS
```

Esto genera `com.randytv.webos_1.0.0_all.ipk`.

---

## ⚠️ PASO 5 — Instalar en el TV

```bash
ares-install --device miLG com.randytv.webos_1.0.0_all.ipk
```

---

## ⚠️ PASO 6 — Lanzar la app

```bash
ares-launch --device miLG com.randytv.webos
```

O ábrela desde el Launcher del TV (aparece como **Randy TV**).

---

## Estructura de archivos

```
RandyTV-webOS/
├── appinfo.json      ← Metadatos webOS (ID, icono, resolución)
├── index.html        ← HTML principal (todas las pantallas)
├── app.css           ← Estilos TV (foco verde, grilla, player)
├── hls.min.js        ← hls.js v0.12.4 ES5 (ver Paso 0)
├── api.js            ← Cliente Xtream + helpers filtros
├── nav.js            ← Motor de navegación por control remoto
├── splash.js         ← Pantalla de carga
├── home.js           ← Menú Home
├── live.js           ← TV en Vivo (categorías + grilla)
├── vod.js            ← Películas (categorías + grilla)
├── player.js         ← Reproductor HLS/VOD + zapping
├── app.js            ← Orquestador principal
└── img/
    ├── icon80.png    ← Ícono app 80×80
    └── icon130.png   ← Ícono app 130×130
```

---

## Control remoto — teclas en cada pantalla

### Home
| Tecla | Acción |
|-------|--------|
| ▲ ▼   | Mover foco entre opciones |
| OK    | Seleccionar opción |

### Lista (Vivo / Películas)
| Tecla | Acción |
|-------|--------|
| ▲ ▼ ◄ ► | Navegar grilla / categorías |
| OK       | Reproducir / seleccionar categoría |
| Back     | Volver a Home |

### Player — TV en Vivo
| Tecla | Acción |
|-------|--------|
| ▲     | Canal siguiente |
| ▼     | Canal anterior |
| OK    | Mostrar/ocultar info |
| Back  | Salir del player |

### Player — Película
| Tecla | Acción |
|-------|--------|
| ◄     | Retroceder 15 s |
| ►     | Adelantar 30 s |
| OK    | Pausar / reanudar |
| ▼     | Mostrar barra de controles |
| Back  | Salir del player |

---

## Problema de CORS

Si ves **"Sin resultados"** en la lista de canales, el servidor Xtream no permite peticiones desde `file://` (CORS).  
Solución: despliega la app en un pequeño servidor HTTP local en tu red:

```bash
# En tu PC
cd RandyTV-webOS
python3 -m http.server 8000
```

Y en el TV abre el navegador en `http://IP_DE_TU_PC:8000`.

---

## Notas técnicas

- **Motor web**: Chromium 53 (2016) — ES5 solamente, sin ES6+.
- **HLS**: hls.js 0.12.x (la última versión ES5 pura). Las versiones 1.x requieren ES6.
- **VOD**: se reproduce directamente con `<video src>`. H.264 + AAC funciona. AC3/DTS puede fallar.
- **No hay Series** en esta v1 — se puede agregar siguiendo el mismo patrón de `vod.js`.

---

*Generado por Kiro para LG 43/49LK5700PSC — webOS 4 / SW 05.50.70*
