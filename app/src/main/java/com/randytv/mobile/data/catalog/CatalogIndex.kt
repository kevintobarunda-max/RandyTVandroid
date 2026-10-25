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
    fun normalize(value: String?): String = separators.replace(
        marks.replace(Normalizer.normalize(value.orEmpty(), Normalizer.Form.NFD), "")
            .lowercase(Locale.ROOT), " ").trim()
}

/** Row identity is independent of filtering; duplicate provider IDs never hide titles. */
data class CatalogEntry<T>(val rowKey: Int, val item: T, val categoryId: String, val searchName: String)

class CatalogIndex<T> private constructor(
    val items: List<T>,
    val entries: List<CatalogEntry<T>>,
    private val groups: Map<String, List<CatalogEntry<T>>>,
    val categoryCounts: Map<String, Int>,
    private val dispatcher: CoroutineDispatcher
) {
    fun count(category: String): Int = if (category == ALL_CATEGORIES) entries.size else groups[category]?.size ?: 0
    fun groupItems(category: String): List<T> = groups[category].orEmpty().map { it.item }

    /** Empty input is O(1); text scans cached names off Main and cancels superseded work. */
    suspend fun query(category: String, text: String, searchAll: Boolean = true): List<CatalogEntry<T>> =
        withContext(dispatcher) {
            val tokens = CatalogText.normalize(text).split(' ').filter { it.isNotEmpty() }
            val source = if (category == ALL_CATEGORIES || (searchAll && tokens.isNotEmpty())) entries
                else groups[category].orEmpty()
            if (tokens.isEmpty()) return@withContext source
            val context = currentCoroutineContext()
            val matches = ArrayList<CatalogEntry<T>>()
            source.forEachIndexed { index, entry ->
                if (index % 128 == 0) context.ensureActive()
                if (tokens.all { entry.searchName.contains(it) }) matches.add(entry)
            }
            matches
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
            CatalogIndex(items, rows, groups, counts, dispatcher)
        }
    }
}
