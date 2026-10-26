package com.randytv.mobile.data.catalog

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Private, versioned snapshots; failures never discard a usable network result. */
class CatalogDiskCache(private val directory: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO) {
    private val gson = Gson()
    suspend fun <T> read(key: String, element: Class<T>): List<T>? = withContext(dispatcher) {
        val file = File(directory, "$key.json")
        if (!file.isFile || file.length() > MAX_BYTES) return@withContext null
        try {
            file.reader().use { gson.fromJson<List<T>>(it, TypeToken.getParameterized(List::class.java, element).type) }
        } catch (_: Exception) { file.delete(); null }
    }
    suspend fun <T> write(key: String, items: List<T>) = withContext(dispatcher) {
        directory.mkdirs()
        val temporary = runCatching { File.createTempFile(key, ".tmp", directory) }.getOrNull() ?: return@withContext
        try {
            directory.mkdirs()
            temporary.outputStream().use { stream ->
                val writer = stream.bufferedWriter()
                gson.toJson(items, writer)
                writer.flush()
                stream.fd.sync()
            }
            if (temporary.length() <= MAX_BYTES) check(temporary.renameTo(File(directory, "$key.json")))
        } catch (_: Exception) { /* Cache is best effort. Keep the previous complete snapshot. */ }
        finally { temporary.delete() }
    }
    companion object { private const val MAX_BYTES = 32L * 1024 * 1024 }
}
