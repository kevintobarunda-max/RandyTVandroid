package com.randytv.mobile.data.repository

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.randytv.mobile.data.model.*
import com.randytv.mobile.data.network.ApiService
import kotlinx.coroutines.*

class DataRepository {
    private val api = ApiService()

    var liveCategories by mutableStateOf<List<Category>>(emptyList())
        private set
    var vodCategories by mutableStateOf<List<Category>>(emptyList())
        private set
    var seriesCategories by mutableStateOf<List<Category>>(emptyList())
        private set
    var liveStreams by mutableStateOf<List<LiveStream>>(emptyList())
        private set
    var vodStreams by mutableStateOf<List<VodStream>>(emptyList())
        private set
    var seriesList by mutableStateOf<List<SeriesItem>>(emptyList())
        private set

    var loadProgress by mutableDoubleStateOf(0.0)
        private set
    var loadDone by mutableStateOf(false)
        private set

    private var loaded = 0
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val simpsonsChannels: List<LiveStream>
        get() = liveStreams.filter { it.name.lowercase().contains("simpson") }

    fun loadAll() {
        loaded = 0
        loadProgress = 0.0
        loadDone = false

        scope.launch {
            // FASE 1: Solo TV en vivo (lo mas importante) - rapido
            val fase1 = listOf(
                async(Dispatchers.IO) { liveCategories = api.getLiveCategories(); tick() },
                async(Dispatchers.IO) { liveStreams = api.getLiveStreams(); tick() }
            )
            fase1.awaitAll()
            Log.d("RandyTV", "TV cargada: ${liveStreams.size} canales")

            // Ir al home inmediatamente
            loadDone = true

            // FASE 2: Cargar pelis y series en segundo plano
            launch(Dispatchers.IO) { vodCategories = api.getVodCategories(); tick() }
            launch(Dispatchers.IO) { seriesCategories = api.getSeriesCategories(); tick() }
            launch(Dispatchers.IO) { vodStreams = api.getVodStreams(); tick() }
            launch(Dispatchers.IO) { seriesList = api.getSeriesList(); tick() }
        }
    }

    @Synchronized
    private fun tick() {
        loaded++
        loadProgress = loaded / 6.0
    }

    suspend fun loadSeriesInfo(seriesId: String): SeriesInfoResponse? {
        return withContext(Dispatchers.IO) { api.getSeriesInfo(seriesId) }
    }

    fun cancel() { scope.cancel() }
}
