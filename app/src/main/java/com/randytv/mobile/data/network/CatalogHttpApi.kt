package com.randytv.mobile.data.network

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.randytv.mobile.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.URLEncoder
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.util.concurrent.TimeUnit

class CatalogHttpApi(private val actionUrl: (String) -> String = { ApiConfig.apiUrl(it) }) : CatalogApi {
    private val categoryDownloads = Semaphore(2)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(5, 30, TimeUnit.SECONDS))
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    private val gson: Gson = GsonBuilder().setLenient().create()

    // Parse directly from the response stream on IO: avoid a second full catalog string.
    private suspend fun <T> fetch(action: String, type: java.lang.reflect.Type): T? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(actionUrl(action))
                    .header("User-Agent", "RandyTV/1.0").build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw java.io.IOException("HTTP ${response.code}")
                    val body = response.body ?: throw java.io.IOException("Respuesta sin contenido")
                    body.charStream().use { reader ->
                        gson.fromJson<T>(reader, type) ?: throw java.io.IOException("Respuesta vacía")
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                throw java.io.IOException("No se pudo descargar el catálogo", error)
            }
        }

    override suspend fun getLiveCategories(): List<Category> =
        fetch<List<Category>>("get_live_categories", TypeToken.getParameterized(List::class.java, Category::class.java).type) ?: emptyList()
    override suspend fun getVodCategories(): List<Category> =
        fetch<List<Category>>("get_vod_categories", TypeToken.getParameterized(List::class.java, Category::class.java).type) ?: emptyList()
    override suspend fun getSeriesCategories(): List<Category> =
        fetch<List<Category>>("get_series_categories", TypeToken.getParameterized(List::class.java, Category::class.java).type) ?: emptyList()
    override suspend fun getLiveStreams(): List<LiveStream> =
        fetch<List<LiveStream>>("get_live_streams", TypeToken.getParameterized(List::class.java, LiveStream::class.java).type) ?: emptyList()
    override suspend fun getVodStreams(): List<VodStream> =
        fetch<List<VodStream>>("get_vod_streams", TypeToken.getParameterized(List::class.java, VodStream::class.java).type) ?: emptyList()
    override suspend fun getSeriesList(): List<SeriesItem> =
        fetch<List<SeriesItem>>("get_series", TypeToken.getParameterized(List::class.java, SeriesItem::class.java).type) ?: emptyList()
    private suspend fun <T> complete(seed: List<T>, categories: List<Category>, action: String,
        type: java.lang.reflect.Type, key: (T) -> String): CatalogCompletion<T> = withContext(Dispatchers.Default) {
        val merged = ArrayList(seed)
        val seen = seed.mapTo(HashSet(), key)
        var failed = 0
        val ids = categories.map { it.categoryId.orEmpty() }.filter { it.isNotBlank() }.distinct()
        for (batch in ids.chunked(2)) {
            val responses = batch.map { id -> async {
                categoryDownloads.withPermit {
                    var result: List<T>? = null
                    for (attempt in 0..1) {
                        try {
                            result = fetch<List<T>>("$action&category_id=${URLEncoder.encode(id, "UTF-8")}", type)
                            break
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { if (attempt == 0) delay(500) }
                    }
                    result
                }
            } }.awaitAll()
            for (rows in responses) {
                if (rows == null) failed++
                else for (row in rows) if (seen.add(key(row))) merged.add(row)
            }
        }
        CatalogCompletion(merged, failed, ids.isNotEmpty())
    }

    override suspend fun completeVod(seed: List<VodStream>, categories: List<Category>) =
        complete(seed, categories, "get_vod_streams",
            TypeToken.getParameterized(List::class.java, VodStream::class.java).type) {
            "${it.streamId}\u0000${it.categoryId}\u0000${it.name}\u0000${it.containerExtension}"
        }
    override suspend fun completeSeries(seed: List<SeriesItem>, categories: List<Category>) =
        complete(seed, categories, "get_series",
            TypeToken.getParameterized(List::class.java, SeriesItem::class.java).type) {
            "${it.seriesId}\u0000${it.categoryId}\u0000${it.name}"
        }

    override suspend fun getSeriesInfo(seriesId: String): SeriesInfoResponse? =
        fetch("get_series_info&series_id=$seriesId", SeriesInfoResponse::class.java)
}
