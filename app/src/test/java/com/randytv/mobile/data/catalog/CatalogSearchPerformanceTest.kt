package com.randytv.mobile.data.catalog

import com.randytv.mobile.data.model.VodStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class CatalogSearchPerformanceTest {
    @Test fun fragmentIndexPreservesSubstringUnicodeAndCategoryResults() = runBlocking {
        val names = listOf("El Señor: Anillos", "ANILLOS del señor", "Camión azul", "東京物語", "a", "", "😀 Sol")
        val movies = names.mapIndexed { i, name -> VodStream(streamId = i, name = name, categoryId = if (i % 2 == 0) "a" else "b") }
        val index = CatalogIndex.build(movies, { it.name }, { it.categoryId },
            extraGroups = mapOf("special" to { it: VodStream -> it.streamId % 3 == 0 }))
        val inputs = listOf("señor an", "ñor", "ANIL", "camion", "az", "東", "京物", "a", "sol", "😀", "zzz", " ")
        for (category in listOf(ALL_CATEGORIES, "a", "b", "special", "missing")) {
            for (all in listOf(true, false)) for (query in inputs) {
                val tokens = CatalogText.normalize(query).split(' ').filter { it.isNotEmpty() }
                val source = if (category == ALL_CATEGORIES || (all && tokens.isNotEmpty())) index.entries
                    else index.query(category, "")
                val expected = source.filter { entry -> tokens.all { entry.searchName.contains(it) } }.map { it.rowKey }
                assertEquals("$category/$all/$query", expected, index.query(category, query, all).map { it.rowKey })
            }
        }
        val first = index.query(ALL_CATEGORIES, "anillos")
        assertSame(first, index.immediate("a", "ANÍLLOS"))
        assertSame(first, index.query("b", "anillos"))
    }

    @Test fun compareUncachedSearchAgainstPreviousFullNameScan() = runBlocking {
        val random = Random(20261025)
        val movies = (1..50000).map { id ->
            val code = (1..12).map { ('a'.code + random.nextInt(26)).toChar() }.joinToString("")
            VodStream(streamId = id, name = "Película $code volumen $id", categoryId = "${id % 30}")
        }
        val index = CatalogIndex.build(movies, { it.name }, { it.categoryId })
        // More than eight distinct queries prevents the small result cache from skewing timings.
        val queries = movies.take(200).map { CatalogText.normalize(it.name).split(' ')[1].substring(2, 8) }
        suspend fun scan(query: String) = withContext(Dispatchers.Default) {
            val tokens = CatalogText.normalize(query).split(' ').filter { it.isNotEmpty() }
            index.entries.filter { entry -> tokens.all { entry.searchName.contains(it) } }
        }
        repeat(20) { i -> scan(queries[i]); index.query(ALL_CATEGORIES, queries[i]) }
        val scanTimes = ArrayList<Long>()
        val indexedTimes = ArrayList<Long>()
        for (query in queries) {
            var start = System.nanoTime()
            val expected = scan(query)
            scanTimes.add(System.nanoTime() - start)
            start = System.nanoTime()
            val actual = index.query(ALL_CATEGORIES, query)
            indexedTimes.add(System.nanoTime() - start)
            assertEquals(expected.map { it.rowKey }, actual.map { it.rowKey })
        }
        val baseline = scanTimes.sorted()[scanTimes.size / 2] / 1000.0
        val optimized = indexedTimes.sorted()[indexedTimes.size / 2] / 1000.0
        println("CATALOG_BENCHMARK rows=50000 queries=200 scan_median_us=$baseline indexed_median_us=$optimized ratio=${baseline / optimized}")
    }
}
