package com.randytv.mobile.data.network

import com.randytv.mobile.data.model.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class CatalogCompletionTest {
    @Test fun categoryDownloadsRecoverMoviesAndSeriesMissingFromGeneralList() = runBlocking {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val action = request.requestUrl!!.queryParameter("action")
                val category = request.requestUrl!!.queryParameter("category_id")
                val body = when {
                    action == "get_vod_streams" && category == "a" -> """[{"stream_id":1,"name":"Primera","category_id":"a"},{"stream_id":2,"name":"Oculta","category_id":"a"}]"""
                    action == "get_vod_streams" -> """[{"stream_id":2,"name":"Otra edición","category_id":"b"}]"""
                    else -> """[{"series_id":9,"name":"Serie oculta","category_id":"a"}]"""
                }
                return MockResponse().setBody(body)
            }
        }
        server.start()
        try {
            val base = server.url("/api").toString()
            val api = CatalogHttpApi { "$base?action=$it" }
            val seed = listOf(VodStream(streamId = 1, name = "Primera", categoryId = "a"))
            val movies = api.completeVod(seed, listOf(Category("a"), Category("b")))
            assertTrue(movies.checked)
            assertEquals(0, movies.failedCategories)
            assertEquals(listOf("Primera", "Oculta", "Otra edición"), movies.items.map { it.name })
            val series = api.completeSeries(emptyList(), listOf(Category("a")))
            assertEquals("Serie oculta", series.items.single().name)
        } finally { server.shutdown() }
    }

    @Test fun failedCategoryIsReportedWithoutLosingPreviouslyLoadedTitles() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(503))
        server.start()
        try {
            val base = server.url("/api").toString()
            val api = CatalogHttpApi { "$base?action=$it" }
            val seed = listOf(SeriesItem(seriesId = 1, name = "Guardada"))
            val result = api.completeSeries(seed, listOf(Category("a")))
            assertEquals(seed, result.items)
            assertEquals(1, result.failedCategories)
            assertEquals(2, server.requestCount)
        } finally { server.shutdown() }
    }
}
