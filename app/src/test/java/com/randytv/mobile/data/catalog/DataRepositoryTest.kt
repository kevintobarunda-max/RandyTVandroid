package com.randytv.mobile.data.catalog

import com.randytv.mobile.data.model.*
import com.randytv.mobile.data.network.CatalogApi
import com.randytv.mobile.data.repository.DataRepository
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DataRepositoryTest {
    private class Api : CatalogApi {
        var failVod = false
        var calls = 0
        override suspend fun getLiveCategories() = listOf(Category("l", "TV"))
        override suspend fun getVodCategories() = listOf(Category("v", "Películas"))
        override suspend fun getSeriesCategories() = listOf(Category("s", "Series"))
        override suspend fun getLiveStreams() = listOf(LiveStream(streamId = 1, name = "TV", categoryId = "l"))
        override suspend fun getVodStreams(): List<VodStream> {
            calls++
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
}
