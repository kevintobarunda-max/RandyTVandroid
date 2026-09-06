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

    private suspend fun fetchJson(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).header("User-Agent", "RandyTV/1.0").build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) response.body?.string() else null
        } catch (e: Exception) { e.printStackTrace(); null }
    }

    suspend fun getLiveCategories(): List<Category> {
        val json = fetchJson(ApiConfig.apiUrl("get_live_categories")) ?: return emptyList()
        return try { gson.fromJson(json, object : TypeToken<List<Category>>() {}.type) } catch (e: Exception) { emptyList() }
    }
    suspend fun getVodCategories(): List<Category> {
        val json = fetchJson(ApiConfig.apiUrl("get_vod_categories")) ?: return emptyList()
        return try { gson.fromJson(json, object : TypeToken<List<Category>>() {}.type) } catch (e: Exception) { emptyList() }
    }
    suspend fun getSeriesCategories(): List<Category> {
        val json = fetchJson(ApiConfig.apiUrl("get_series_categories")) ?: return emptyList()
        return try { gson.fromJson(json, object : TypeToken<List<Category>>() {}.type) } catch (e: Exception) { emptyList() }
    }
    suspend fun getLiveStreams(): List<LiveStream> {
        val json = fetchJson(ApiConfig.apiUrl("get_live_streams")) ?: return emptyList()
        return try { gson.fromJson(json, object : TypeToken<List<LiveStream>>() {}.type) } catch (e: Exception) { emptyList() }
    }
    suspend fun getVodStreams(): List<VodStream> {
        val json = fetchJson(ApiConfig.apiUrl("get_vod_streams")) ?: return emptyList()
        return try { gson.fromJson(json, object : TypeToken<List<VodStream>>() {}.type) } catch (e: Exception) { emptyList() }
    }
    suspend fun getSeriesList(): List<SeriesItem> {
        val json = fetchJson(ApiConfig.apiUrl("get_series")) ?: return emptyList()
        return try { gson.fromJson(json, object : TypeToken<List<SeriesItem>>() {}.type) } catch (e: Exception) { emptyList() }
    }
    suspend fun getSeriesInfo(seriesId: String): SeriesInfoResponse? {
        val json = fetchJson(ApiConfig.apiUrl("get_series_info&series_id=$seriesId")) ?: return null
        return try { gson.fromJson(json, SeriesInfoResponse::class.java) } catch (e: Exception) { null }
    }
}
