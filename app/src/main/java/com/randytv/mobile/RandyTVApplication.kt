package com.randytv.mobile

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * Application personalizada para dispositivos de POCA RAM (proyector Android 12, 2GB).
 *
 * Configura Coil con limites de cache conservadores. Por defecto Coil usa hasta un 25% de la RAM
 * disponible de la app para la cache de imagenes en memoria; en un equipo de 2GB eso presiona
 * demasiado la memoria y provoca lentitud y cierres. Aqui limitamos:
 *  - Cache en memoria: 15% (portadas/logos recientes, acceso instantaneo)
 *  - Cache en disco: 128 MB (evita re-descargar imagenes y ahorra datos/CPU)
 */
class RandyTVApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(128L * 1024 * 1024) // 128 MB
                    .build()
            }
            // Respeta el poco almacenamiento (16 GB) y evita mantener bitmaps completos en RAM.
            .respectCacheHeaders(false)
            .build()
    }
}
