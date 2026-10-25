package com.randytv.mobile.data.network

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.randytv.mobile.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.util.concurrent.TimeUnit

class CatalogHttpApi : CatalogApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(5, 30, TimeUnit.SECONDS))
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    private val gson: Gson = GsonBuilder().setLenient().create()

    // Parse directly from the response stream on IO: avoid a second full catalog string.
    private suspend fun <T> fetch(action: String, type: java.lang.reflect.Type): T? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(ApiConfig.apiUrl(action))
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
    override suspend fun getSeriesInfo(seriesId: String): SeriesInfoResponse? =
        fetch("get_series_info&series_id=$seriesId", SeriesInfoResponse::class.java)
}
