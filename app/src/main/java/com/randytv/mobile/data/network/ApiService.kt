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

object ApiConfig {
    const val SERVER = "http://tv.streamid.tv:8080"
    const val USERNAME = "cristobalignacio6834"
    const val PASSWORD = "Ajud4CU6dH3Q"

    // Usar m3u8 para mejor compatibilidad de audio
    fun liveUrl(streamId: String): String =
        "$SERVER/live/$USERNAME/$PASSWORD/$streamId.m3u8"

    fun vodUrl(streamId: String, ext: String): String =
        "$SERVER/movie/$USERNAME/$PASSWORD/$streamId.$ext"

    fun seriesUrl(episodeId: String, ext: String): String =
        "$SERVER/series/$USERNAME/$PASSWORD/$episodeId.$ext"

    fun apiUrl(action: String): String =
        "$SERVER/player_api.php?username=$USERNAME&password=$PASSWORD&action=$action"
}

class ApiService {
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
                    if (!response.isSuccessful) return@withContext null
                    response.body?.charStream()?.use { reader -> gson.fromJson<T>(reader, type) }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }

    suspend fun getLiveCategories(): List<Category> =
        fetch<List<Category>>("get_live_categories", TypeToken.getParameterized(List::class.java, Category::class.java).type) ?: emptyList()
    suspend fun getVodCategories(): List<Category> =
        fetch<List<Category>>("get_vod_categories", TypeToken.getParameterized(List::class.java, Category::class.java).type) ?: emptyList()
    suspend fun getSeriesCategories(): List<Category> =
        fetch<List<Category>>("get_series_categories", TypeToken.getParameterized(List::class.java, Category::class.java).type) ?: emptyList()
    suspend fun getLiveStreams(): List<LiveStream> =
        fetch<List<LiveStream>>("get_live_streams", TypeToken.getParameterized(List::class.java, LiveStream::class.java).type) ?: emptyList()
    suspend fun getVodStreams(): List<VodStream> =
        fetch<List<VodStream>>("get_vod_streams", TypeToken.getParameterized(List::class.java, VodStream::class.java).type) ?: emptyList()
    suspend fun getSeriesList(): List<SeriesItem> =
        fetch<List<SeriesItem>>("get_series", TypeToken.getParameterized(List::class.java, SeriesItem::class.java).type) ?: emptyList()
    suspend fun getSeriesInfo(seriesId: String): SeriesInfoResponse? =
        fetch("get_series_info&series_id=$seriesId", SeriesInfoResponse::class.java)
}
