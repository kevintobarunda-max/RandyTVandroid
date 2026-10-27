package com.randytv.mobile.data.catalog

import com.google.gson.Gson
import com.randytv.mobile.data.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CatalogIndexTest {
    @Test fun fastNormalizationMatchesUnicodePathForAsciiNames() {
        val samples = listOf("  STAR_WARS: The-Force!  ", "A/B 123", "", "?!", "el senor", "ABC\tDEF\nGHI")
        for (sample in samples) {
            val expected = sample.lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim()
            assertEquals(expected, CatalogText.normalize(sample))
        }
        assertEquals("senor camion", CatalogText.normalize("Señor CAMIÓN"))
    }

    @Test fun categoryReturnsEveryMovieBeyondTheOldLimit() = runBlocking {
        val movies = (1..250).map { VodStream(streamId = it, name = "Película $it", categoryId = "a") }
        val index = CatalogIndex.build(movies, { it.name }, { it.categoryId })
        assertEquals(250, index.query("a", "").size)
        assertEquals("Película 250", index.query("a", "").last().item.name)
        assertEquals(250, index.count("a"))
    }

    @Test fun allSeriesAreRetainedIncludingOrphansAndNamesThatUsedToBeExcluded() = runBlocking {
        val series = (1..180).map { SeriesItem(seriesId = it, name = "Serie $it", categoryId = "s") } +
            listOf(SeriesItem(seriesId = 181, name = "Vivid: ciencia", categoryId = "missing"),
                SeriesItem(seriesId = 182, name = "Adultos novatos", categoryId = ""))
        val index = CatalogIndex.build(series, { it.name }, { it.categoryId })
        assertEquals(182, index.query(ALL_CATEGORIES, "").size)
        val categories = index.categories(listOf(Category("s", "Series")))
        assertTrue(categories.any { it.categoryId == "missing" })
        assertTrue(categories.any { it.categoryId == "" })
        assertEquals(1, index.query(ALL_CATEGORIES, "vivid").size)
    }

    @Test fun normalizedSearchCrossesCategoriesAndMatchesAllWords() = runBlocking {
        val movies = listOf(VodStream(streamId = 1, name = "El Señor de los Anillos", categoryId = "b"),
            VodStream(streamId = 2, name = "Otra película", categoryId = "a"))
        val index = CatalogIndex.build(movies, { it.name }, { it.categoryId })
        assertEquals(1, index.query("a", "senor anillos").single().item.streamId)
        assertTrue(index.query("a", "senor anillos", searchAll = false).isEmpty())
        assertEquals(1, index.query("a", "").size)
        assertTrue(index.query(ALL_CATEGORIES, "ningun titulo").isEmpty())
    }

    @Test fun duplicateProviderIdsHaveDistinctStableRowKeys() = runBlocking {
        val movies = listOf(VodStream(streamId = 1, name = "Versión A"),
            VodStream(streamId = 1, name = "Versión B"))
        val index = CatalogIndex.build(movies, { it.name }, { it.categoryId })
        assertEquals(2, index.entries.map { it.rowKey }.distinct().size)
        assertEquals(index.entries[1].rowKey, index.query(ALL_CATEGORIES, "version b").single().rowKey)
    }

    @Test fun nullableProviderMetadataDoesNotDropTheWholeCatalog() = runBlocking {
        val row = Gson().fromJson("""{"stream_id":1,"name":null,"category_id":null,"stream_icon":null}""", VodStream::class.java)
        val index = CatalogIndex.build(listOf(row), { it.name }, { it.categoryId })
        assertEquals(1, index.query(ALL_CATEGORIES, "").size)
        assertEquals("", row.getImageUrl())
        assertEquals("Sin categoría", index.categories(emptyList()).single().categoryName)
    }

    @Test fun largeCatalogIsNotTruncatedDuringSearch() = runBlocking {
        val series = (1..15000).map { SeriesItem(seriesId = it, name = "Historia $it", categoryId = "s") }
        val index = CatalogIndex.build(series, { it.name }, { it.categoryId })
        assertEquals(15000, index.query(ALL_CATEGORIES, "HISTORIA").size)
        assertEquals(15000, index.query(ALL_CATEGORIES, "historia 15000").single().item.seriesId)
    }
}
