package com.randytv.mobile.data.catalog

import com.randytv.mobile.data.model.*
import com.randytv.mobile.data.network.CatalogApi
import com.randytv.mobile.data.repository.DataRepository
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DataRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private class Api : CatalogApi {
        var failVod = false
        var gate: CompletableDeferred<Unit>? = null
        var calls = 0
        override suspend fun getLiveCategories() = listOf(Category("l", "TV"))
        override suspend fun getVodCategories() = listOf(Category("v", "Películas"))
        override suspend fun getSeriesCategories() = listOf(Category("s", "Series"))
        override suspend fun getLiveStreams() = listOf(LiveStream(streamId = 1, name = "TV", categoryId = "l"))
        override suspend fun getVodStreams(): List<VodStream> {
            calls++
            gate?.await()
            if (failVod) throw IOException("offline")
            return (1..250).map { VodStream(streamId = it, name = "Película $it", categoryId = "v") }
        }
        override suspend fun getSeriesList() =
            (1..180).map { SeriesItem(seriesId = it, name = "Serie $it", categoryId = "s") }
        override suspend fun getSeriesInfo(seriesId: String): SeriesInfoResponse? = null
    }

    @Test fun failedMoviesDoNotCancelSeriesAndRetryCanRecover() = runTest {
        val api = Api().apply { failVod = true }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = DataRepository(api, dispatcher, dispatcher)
        try {
            repo.loadAll().join()
            assertEquals(2, api.calls)
            assertNotNull(repo.vodError)
            assertFalse(repo.vodLoading)
            assertEquals(180, repo.seriesList.size)
            assertEquals(1, repo.liveStreams.size)
            api.failVod = false
            repo.reloadVod().join()
            assertNull(repo.vodError)
            assertEquals(250, repo.vodStreams.size)
            assertEquals(180, repo.seriesList.size)
        } finally { repo.cancel() }
    }

    @Test fun refreshFailureKeepsPreviouslyDownloadedTitles() = runTest {
        val api = Api()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = DataRepository(api, dispatcher, dispatcher)
        try {
            repo.reloadVod().join()
            val previous = repo.vodIndex
            api.failVod = true
            repo.reloadVod().join()
            assertSame(previous, repo.vodIndex)
            assertEquals(250, repo.vodStreams.size)
            assertNotNull(repo.vodError)
        } finally { repo.cancel() }
    }
    @Test fun cachedTitlesAreSearchableBeforeSlowNetworkFinishes() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cache = CatalogDiskCache(folder.root, dispatcher)
        cache.write("vod", listOf(VodStream(streamId = 99, name = "Película guardada")))
        val gate = CompletableDeferred<Unit>()
        val api = Api().apply { this.gate = gate }
        val repo = DataRepository(api, dispatcher, dispatcher, cache)
        try {
            val job = repo.reloadVod()
            runCurrent()
            assertFalse(job.isCompleted)
            assertFalse(repo.vodLoading)
            assertEquals(1, repo.vodIndex.query(ALL_CATEGORIES, "guardada").size)
            gate.complete(Unit)
            job.join()
            assertEquals(250, repo.vodStreams.size)
            val previous = repo.vodIndex
            repo.reloadVod().join()
            assertSame("An unchanged refresh must not rebuild search indexes or redraw all cards", previous, repo.vodIndex)
        } finally { repo.cancel() }
    }

}
