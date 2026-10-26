package com.randytv.mobile.data.repository

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.randytv.mobile.data.catalog.*
import com.randytv.mobile.data.model.*
import com.randytv.mobile.data.network.CatalogHttpApi
import com.randytv.mobile.data.network.CatalogApi
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class DataRepository(
    private val api: CatalogApi = CatalogHttpApi(),
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val cache: CatalogDiskCache? = null
) {
    var liveCategories by mutableStateOf<List<Category>>(emptyList())
        private set
    var vodCategories by mutableStateOf<List<Category>>(emptyList())
        private set
    var seriesCategories by mutableStateOf<List<Category>>(emptyList())
        private set
    var liveIndex by mutableStateOf(CatalogIndex.empty<LiveStream>(computationDispatcher))
        private set
    var vodIndex by mutableStateOf(CatalogIndex.empty<VodStream>(computationDispatcher))
        private set
    var seriesIndex by mutableStateOf(CatalogIndex.empty<SeriesItem>(computationDispatcher))
        private set
    val liveStreams: List<LiveStream> get() = liveIndex.items
    val vodStreams: List<VodStream> get() = vodIndex.items
    val seriesList: List<SeriesItem> get() = seriesIndex.items
    val simpsonsChannels: List<LiveStream> get() = liveIndex.groupItems(SIMPSONS)

    var liveLoading by mutableStateOf(true)
        private set
    var vodLoading by mutableStateOf(true)
        private set
    var seriesLoading by mutableStateOf(true)
        private set
    var liveError by mutableStateOf<String?>(null)
        private set
    var vodError by mutableStateOf<String?>(null)
        private set
    var seriesError by mutableStateOf<String?>(null)
        private set
    val loadError: String? get() = liveError
    var loadProgress by mutableDoubleStateOf(0.0)
        private set
    var loadDone by mutableStateOf(false)
        private set

    private val scope = CoroutineScope(mainDispatcher + SupervisorJob())
    // Two streaming downloads overlap, bounded for a 2 GB device.
    private val catalogDownload = Semaphore(2)
    private var liveJob: Job? = null
    private var vodJob: Job? = null
    private var seriesJob: Job? = null
    private var loaded = 0

    fun loadAll(): Job {
        loaded = 0
        loadProgress = 0.0
        val jobs = listOf(reloadLive(), reloadVod(), reloadSeries())
        return scope.launch { jobs.joinAll() }
    }

    private fun tick() { loaded++; loadProgress = (loaded / 6.0).coerceAtMost(1.0) }

    private suspend fun <T> retry(block: suspend () -> T): Result<T> {
        repeat(2) { attempt ->
            try { return Result.success(block()) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (attempt == 1) return Result.failure(error)
                delay(500)
            }
        }
        error("unreachable")
    }

    fun reloadLive(): Job {
        liveJob?.cancel()
        liveLoading = true
        liveError = null
        val job = scope.launch {
            if (liveIndex.items.isEmpty()) {
                cache?.read("live-categories", Category::class.java)?.let { liveCategories = it }
                cache?.read("live", LiveStream::class.java)?.let { cached ->
                    liveIndex = CatalogIndex.build(cached, { row -> row.name }, { row -> row.categoryId },
                        mapOf(LOW_BITRATE to ChannelGroups::isLowBitrate,
                            ROUND_THE_CLOCK to ChannelGroups::is24Hour, SIMPSONS to ChannelGroups::isSimpsons),
                        dispatcher = computationDispatcher)
                    liveLoading = false
                }
            }
            val categoryRequest = async { retry { api.getLiveCategories() } }
            val items = retry { catalogDownload.withPermit { api.getLiveStreams() } }
            items.getOrNull()?.let {
                if (withContext(computationDispatcher) { it != liveIndex.items }) liveIndex = CatalogIndex.build(it, { row -> row.name }, { row -> row.categoryId },
                    mapOf(LOW_BITRATE to ChannelGroups::isLowBitrate,
                        ROUND_THE_CLOCK to ChannelGroups::is24Hour, SIMPSONS to ChannelGroups::isSimpsons),
                    computationDispatcher)
            }
            liveLoading = false
            val categories = categoryRequest.await()
            categories.getOrNull()?.let { liveCategories = it; cache?.write("live-categories", it) }
            tick()
            liveError = when {
                items.isFailure -> "No se pudieron cargar los canales. Reintenta."
                categories.isFailure -> "No se pudieron actualizar los nombres de categorías."
                else -> null
            }
            liveLoading = false
            items.getOrNull()?.let { cache?.write("live", it) }
            loadDone = true
            tick()
        }
        liveJob = job
        return job
    }

    fun reloadVod(): Job {
        vodJob?.cancel()
        vodLoading = true
        vodError = null
        val job = scope.launch {
            if (vodIndex.items.isEmpty()) {
                cache?.read("vod-categories", Category::class.java)?.let { vodCategories = it }
                cache?.read("vod", VodStream::class.java)?.let { cached ->
                    vodIndex = CatalogIndex.build(cached, { row -> row.name }, { row -> row.categoryId },
                        dispatcher = computationDispatcher)
                    vodLoading = false
                }
            }
            val categoryRequest = async { retry { api.getVodCategories() } }
            val items = retry { catalogDownload.withPermit { api.getVodStreams() } }
            items.getOrNull()?.let {
                if (withContext(computationDispatcher) { it != vodIndex.items }) vodIndex = CatalogIndex.build(it, { row -> row.name }, { row -> row.categoryId },
                    dispatcher = computationDispatcher)
            }
            vodLoading = false
            val categories = categoryRequest.await()
            categories.getOrNull()?.let { vodCategories = it; cache?.write("vod-categories", it) }
            tick()
            vodError = when {
                items.isFailure -> "No se pudieron actualizar las películas. Reintenta."
                categories.isFailure -> "Faltan nombres de categorías; los títulos descargados siguen disponibles."
                else -> null
            }
            vodLoading = false
            items.getOrNull()?.let { cache?.write("vod", it) }
            tick()
        }
        vodJob = job
        return job
    }

    fun reloadSeries(): Job {
        seriesJob?.cancel()
        seriesLoading = true
        seriesError = null
        val job = scope.launch {
            if (seriesIndex.items.isEmpty()) {
                cache?.read("series-categories", Category::class.java)?.let { seriesCategories = it }
                cache?.read("series", SeriesItem::class.java)?.let { cached ->
                    seriesIndex = CatalogIndex.build(cached, { row -> row.name }, { row -> row.categoryId },
                        dispatcher = computationDispatcher)
                    seriesLoading = false
                }
            }
            val categoryRequest = async { retry { api.getSeriesCategories() } }
            val items = retry { catalogDownload.withPermit { api.getSeriesList() } }
            items.getOrNull()?.let {
                if (withContext(computationDispatcher) { it != seriesIndex.items }) seriesIndex = CatalogIndex.build(it, { row -> row.name }, { row -> row.categoryId },
                    dispatcher = computationDispatcher)
            }
            seriesLoading = false
            val categories = categoryRequest.await()
            categories.getOrNull()?.let { seriesCategories = it; cache?.write("series-categories", it) }
            tick()
            seriesError = when {
                items.isFailure -> "No se pudieron actualizar las series. Reintenta."
                categories.isFailure -> "Faltan nombres de categorías; los títulos descargados siguen disponibles."
                else -> null
            }
            seriesLoading = false
            items.getOrNull()?.let { cache?.write("series", it) }
            tick()
        }
        seriesJob = job
        return job
    }

    suspend fun loadSeriesInfo(seriesId: String): SeriesInfoResponse? =
        retry { api.getSeriesInfo(seriesId) }.getOrNull()

    fun cancel() { scope.cancel() }
}
