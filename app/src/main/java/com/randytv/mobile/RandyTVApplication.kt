package com.randytv.mobile

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/** Shared, bounded artwork cache. Video samples have their own separate budget. */
class RandyTVApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizeBytes(16 * 1024 * 1024).build() }
        .diskCache {
            DiskCache.Builder().directory(cacheDir.resolve("artwork"))
                .maxSizeBytes(64L * 1024 * 1024).build()
        }
        .crossfade(false)
        .build()
}
