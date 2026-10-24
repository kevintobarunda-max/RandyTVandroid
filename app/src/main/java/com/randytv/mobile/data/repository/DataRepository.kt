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

    var loadError by mutableStateOf<String?>(null)
        private set
    private var loadJob: Job? = null
    private var loaded = 0
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob() +
        CoroutineExceptionHandler { _, error ->
            Log.e("RandyTV", "Catalog load failed: ${error.javaClass.simpleName}")
            loadError = "No se pudo cargar el catálogo. Comprueba la conexión y pulsa Reintentar."
            loadDone = true
        })

    var simpsonsChannels by mutableStateOf<List<LiveStream>>(emptyList())
        private set

    fun loadAll() {
        loadJob?.cancel()
        loadError = null
        loaded = 0
        loadProgress = 0.0
        loadDone = false

        loadJob = scope.launch {
            // FASE 1: Solo TV en vivo (lo mas importante) - rapido
            val fase1 = listOf(
                async { liveCategories = api.getLiveCategories(); tick() },
                async { liveStreams = api.getLiveStreams(); simpsonsChannels = liveStreams.filter { it.name.contains("simpson", true) }; tick() }
            )
            fase1.awaitAll()
            Log.d("RandyTV", "TV cargada: ${liveStreams.size} canales")

            // Ir al home inmediatamente
            loadDone = true
            if (liveStreams.isEmpty()) {
                loadError = "El catálogo está vacío o no se pudo conectar. Pulsa Reintentar."
            }

            // FASE 2: Cargar pelis y series en segundo plano
            // Serialize large catalogs to avoid concurrent JSON allocation peaks.
            vodCategories = api.getVodCategories(); tick()
            seriesCategories = api.getSeriesCategories(); tick()
            vodStreams = api.getVodStreams(); tick()
            seriesList = api.getSeriesList(); tick()
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
