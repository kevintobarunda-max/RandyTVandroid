package com.randytv.mobile.data.catalog

import com.randytv.mobile.data.model.VodStream
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CatalogDiskCacheTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun completeCatalogSurvivesANewCacheInstanceAndCorruptionIsIgnored() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val items = (1..1500).map { VodStream(streamId = it, name = "Película $it") }
        CatalogDiskCache(folder.root, dispatcher).write("vod", items)
        assertEquals(items, CatalogDiskCache(folder.root, dispatcher).read("vod", VodStream::class.java))
        folder.root.resolve("vod.json").writeText("{broken")
        assertNull(CatalogDiskCache(folder.root, dispatcher).read("vod", VodStream::class.java))
    }
}
