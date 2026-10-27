package com.randytv.mobile.data.catalog

import com.randytv.mobile.data.model.Category
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

const val ALL_CATEGORIES = "@all"
const val LOW_BITRATE = "@2mb"
const val ROUND_THE_CLOCK = "@24h"
const val SIMPSONS = "@simpsons"

object CatalogText {
    private val marks = Regex("\\p{M}+")
    private val separators = Regex("[^\\p{L}\\p{N}]+")
    fun normalize(value: String?): String {
        val text = value.orEmpty()
        // Most provider names/keystrokes are ASCII: avoid three regex/Unicode passes.
        if (text.all { it.code < 128 }) {
            val result = StringBuilder(text.length)
            var separator = false
            for (character in text) {
                val letter = if (character in 'A'..'Z') character + 32 else character
                if (letter in 'a'..'z' || letter in '0'..'9') {
                    if (separator && result.isNotEmpty()) result.append(' ')
                    result.append(letter)
                    separator = false
                } else separator = true
            }
            return result.toString()
        }
        return separators.replace(
            marks.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "")
                .lowercase(Locale.ROOT), " ").trim()
    }
}

/** Row identity is independent of filtering; duplicate provider IDs never hide titles. */
data class CatalogEntry<T>(val rowKey: Int, val item: T, val categoryId: String, val searchName: String)

class CatalogIndex<T> private constructor(
    val items: List<T>,
    val entries: List<CatalogEntry<T>>,
    private val groups: Map<String, List<CatalogEntry<T>>>,
    val categoryCounts: Map<String, Int>,
    private val dispatcher: CoroutineDispatcher,
    private val postings: Array<IntArray>? = null
) {
    fun count(category: String): Int = if (category == ALL_CATEGORIES) entries.size else groups[category]?.size ?: 0
    fun groupItems(category: String): List<T> = groups[category].orEmpty().map { it.item }

    // At most eight small result sets; never retain another unbounded catalog copy.
    private val cache = object : LinkedHashMap<String, List<CatalogEntry<T>>>(8, .75f, true) {}
    private var cachedRows = 0
    private fun cacheKey(category: String, normalized: String, searchAll: Boolean) =
        "${if (searchAll) ALL_CATEGORIES else category}\u0000$normalized"

    fun immediate(category: String, text: String, searchAll: Boolean = true): List<CatalogEntry<T>>? {
        val normalized = CatalogText.normalize(text)
        if (normalized.isEmpty()) return if (category == ALL_CATEGORIES) entries else groups[category].orEmpty()
        return synchronized(cache) { cache[cacheKey(category, normalized, searchAll)] }
    }

    /** Rare trigram candidates, verified against the full name: collisions cannot lose titles. */
    suspend fun query(category: String, text: String, searchAll: Boolean = true): List<CatalogEntry<T>> {
        immediate(category, text, searchAll)?.let { return it }
        return withContext(dispatcher) {
            val normalized = CatalogText.normalize(text)
            val tokens = normalized.split(' ').filter { it.isNotEmpty() }
            val context = currentCoroutineContext()
            var candidates: IntArray? = null
            postings?.let { buckets ->
                for (token in tokens) for (i in 0 until token.length - 2) {
                    val ids = buckets[bucket(token[i], token[i + 1], token[i + 2])]
                    if (candidates == null || ids.size < candidates!!.size) candidates = ids
                }
            }
            val matches = ArrayList<CatalogEntry<T>>()
            val ids = candidates
            if (ids != null) {
                // Membership by row ID also supports synthetic live channel groups.
                val allowed = if (!searchAll && category != ALL_CATEGORIES)
                    groups[category].orEmpty().mapTo(HashSet()) { it.rowKey } else null
                ids.forEachIndexed { i, id ->
                    if (i % 128 == 0) context.ensureActive()
                    val entry = entries[id]
                    if ((allowed == null || id in allowed) && tokens.all { entry.searchName.contains(it) }) matches.add(entry)
                }
            } else {
                val source = if (searchAll || category == ALL_CATEGORIES) entries else groups[category].orEmpty()
                source.forEachIndexed { i, entry ->
                    if (i % 128 == 0) context.ensureActive()
                    if (tokens.all { entry.searchName.contains(it) }) matches.add(entry)
                }
            }
            context.ensureActive()
            if (matches.size <= 32768) synchronized(cache) {
                val key = cacheKey(category, normalized, searchAll)
                cachedRows -= cache.remove(key)?.size ?: 0
                cache[key] = matches
                cachedRows += matches.size
                while (cache.size > 8 || cachedRows > 32768) {
                    val oldest = cache.entries.iterator()
                    cachedRows -= oldest.next().value.size
                    oldest.remove()
                }
            }
            matches
        }
    }

    /** Keep all server categories and synthesize labels for orphaned category IDs. */
    fun categories(serverCategories: List<Category>): List<Category> {
        val result = LinkedHashMap<String, Category>()
        serverCategories.forEach { category ->
            val id = category.categoryId.orEmpty()
            result[id] = Category(id, category.categoryName.orEmpty().ifBlank {
                if (id.isEmpty()) "Sin categoría" else "Categoría $id"
            })
        }
        categoryCounts.keys.forEach { id ->
            if (id !in result) result[id] = Category(id, if (id.isEmpty()) "Sin categoría" else "Categoría $id")
        }
        return result.values.toList()
    }

    companion object {
        private const val BUCKETS = 32768
        private fun bucket(a: Char, b: Char, c: Char): Int =
            ((a.code * 31 + b.code) * 31 + c.code) and (BUCKETS - 1)

        // Primitive arrays avoid a String/boxed-Integer object for every name fragment.
        // Bound posting payload to 8 MiB; larger catalogs retain the exact scan fallback.
        private suspend fun <T> buildPostings(rows: List<CatalogEntry<T>>): Array<IntArray>? {
            val context = currentCoroutineContext()
            val counts = IntArray(BUCKETS)
            val seen = IntArray(BUCKETS) { -1 }
            var total = 0
            rows.forEachIndexed { id, row ->
                if (id % 128 == 0) context.ensureActive()
                for (i in 0 until row.searchName.length - 2) {
                    val key = bucket(row.searchName[i], row.searchName[i + 1], row.searchName[i + 2])
                    if (seen[key] != id) {
                        seen[key] = id
                        counts[key]++
                        if (++total > 2_000_000) return null
                    }
                }
            }
            val empty = IntArray(0)
            val result = Array(BUCKETS) { if (counts[it] == 0) empty else IntArray(counts[it]) }
            counts.fill(0)
            seen.fill(-1)
            rows.forEachIndexed { id, row ->
                if (id % 128 == 0) context.ensureActive()
                for (i in 0 until row.searchName.length - 2) {
                    val key = bucket(row.searchName[i], row.searchName[i + 1], row.searchName[i + 2])
                    if (seen[key] != id) {
                        seen[key] = id
                        result[key][counts[key]++] = id
                    }
                }
            }
            return result
        }

        fun <T> empty(dispatcher: CoroutineDispatcher = Dispatchers.Default): CatalogIndex<T> =
            CatalogIndex(emptyList(), emptyList(), emptyMap(), emptyMap(), dispatcher)

        suspend fun <T> build(
            items: List<T>,
            title: (T) -> String?,
            category: (T) -> String?,
            extraGroups: Map<String, (T) -> Boolean> = emptyMap(),
            dispatcher: CoroutineDispatcher = Dispatchers.Default
        ): CatalogIndex<T> = withContext(dispatcher) {
            val context = currentCoroutineContext()
            val rows = ArrayList<CatalogEntry<T>>(items.size)
            val groups = LinkedHashMap<String, MutableList<CatalogEntry<T>>>()
            val counts = LinkedHashMap<String, Int>()
            items.forEachIndexed { index, item ->
                if (index % 128 == 0) context.ensureActive()
                val id = category(item).orEmpty()
                val entry = CatalogEntry(index, item, id, CatalogText.normalize(title(item)))
                rows.add(entry)
                groups.getOrPut(id) { ArrayList() }.add(entry)
                counts[id] = (counts[id] ?: 0) + 1
                extraGroups.forEach { (key, predicate) ->
                    if (predicate(item)) groups.getOrPut(key) { ArrayList() }.add(entry)
                }
            }
            CatalogIndex(items, rows, groups, counts, dispatcher, buildPostings(rows))
        }
    }
}
