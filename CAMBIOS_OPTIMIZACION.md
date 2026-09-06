# Optimización para proyector Android 12 (2 GB RAM / 16 GB)

Este documento resume los cambios hechos para dos objetivos:

1. **Compatibilidad total de audio** — que se escuchen los títulos que antes quedaban mudos.
2. **App más liviana y rápida** — para un proyector con solo 2 GB de RAM.

---

## 1. Audio: todos los códecs se escuchan 🔊

**Problema:** muchos canales/películas traen audio en **AC-3, E-AC3, DTS, MP2, TrueHD**, etc.
El proyector no tiene decodificador de *hardware* para esos formatos, así que el video se veía
pero **el audio no sonaba**. El código intentaba activar decodificadores de software, pero le
faltaba la librería FFmpeg que los provee.

**Solución:** se integró **[NextLib](https://github.com/anilbeesetti/nextlib)**
(`io.github.anilbeesetti:nextlib-media3ext`), que trae los decodificadores FFmpeg **ya compilados**
para Media3. Ahora el reproductor usa `NextRenderersFactory` con `EXTENSION_RENDERER_MODE_PREFER`:
cuando el hardware no soporta un códec de audio, se decodifica por **software** y se escucha.

Códecs de audio soportados ahora: Vorbis, Opus, FLAC, ALAC, PCM (mulaw/alaw), MP3, AMR-NB, AMR-WB,
AAC, **AC3, E-AC3, DTS (dca), MLP, TrueHD**.

Archivos: `app/build.gradle.kts`, `PlayerController.kt`.

---

## 2. Más liviana y rápida ⚡

| Cambio | Antes | Ahora | Beneficio |
|---|---|---|---|
| Buffer del reproductor | hasta 120 s | hasta 30 s | Menos RAM ocupada, arranque igual de rápido |
| `android:largeHeap` | activado | **quitado** | En 2 GB, pedir heap gigante empeora la memoria; sin él el sistema gestiona mejor |
| Caché de imágenes (Coil) | ~25 % de RAM (por defecto) | 15 % RAM + 128 MB disco | La app no acapara memoria con portadas/logos |
| Arquitecturas (ABI) | 4 (universal) | armeabi-v7a + arm64-v8a, con **APK por arquitectura** | APK mucho más pequeño para instalar |
| Idiomas empaquetados | todos | es / en | APK más pequeño |

Archivos: `PlayerController.kt`, `AndroidManifest.xml`, `RandyTVApplication.kt` (nuevo),
`app/build.gradle.kts`.

---

## 3. Estabilidad del build de release 🛡️

Se añadió `app/proguard-rules.pro` para que R8 (minify de release) **no elimine ni ofusque** las
clases nativas de NextLib/FFmpeg ni las de Media3. Sin esto, el audio por software funcionaría en
debug pero podría romperse en el APK de release.

Además se alineó **Media3 a la versión 1.5.1** (la misma que empaqueta NextLib 0.8.4) para evitar
conflictos de versiones entre el ExoPlayer de la app y el de la extensión.

---

## Cómo compilar e instalar

En Android Studio (en tu Mac):

1. **Sync Gradle** (descargará NextLib y Media3 1.5.1 la primera vez, necesita internet).
2. Genera el APK:
   - Rápido para probar: **Build → Build APK(s)** (debug).
   - Para instalar definitivo: build **release**.
3. Con los **ABI splits**, obtendrás varios APKs. Para un proyector Android 12 normal, instala el
   **`arm64-v8a`**. Si ese no instala, prueba el **`armeabi-v7a`**, o el **universal** (sirve en
   cualquiera, pero pesa más).

> Nota: NextLib incluye librerías nativas (FFmpeg). La primera compilación tras el cambio puede
> tardar un poco más mientras Gradle descarga las dependencias.
