package com.randytv.mobile.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.randytv.mobile.data.model.*
import com.randytv.mobile.data.network.CatalogApi
import com.randytv.mobile.data.repository.DataRepository
import com.randytv.mobile.ui.screens.ListScreen
import com.randytv.mobile.ui.screens.ListType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogUiTest {
    @get:Rule val compose = createAndroidComposeRule<CatalogTestActivity>()

    @Test fun categoryBeyond100AndGlobalAccentSearchAreUsable() {
        val api = object : CatalogApi {
            override suspend fun getLiveCategories() = emptyList<Category>()
            override suspend fun getSeriesCategories() = emptyList<Category>()
            override suspend fun getVodCategories() = listOf(Category("a", "Categoría uno"), Category("b", "Fantasía"))
            override suspend fun getLiveStreams() = emptyList<LiveStream>()
            override suspend fun getSeriesList() = emptyList<SeriesItem>()
            override suspend fun getVodStreams() =
                (0..139).map { VodStream(streamId = it, name = "Película número $it", categoryId = "a") } +
                    VodStream(streamId = 999, name = "El Señor de los Anillos", categoryId = "b")
            override suspend fun getSeriesInfo(seriesId: String): SeriesInfoResponse? = null
        }
        val repository = DataRepository(api)
        try {
            compose.setContent {
                MaterialTheme {
                    ListScreen(ListType.VOD, repository = repository, onHome = {},
                        onTapLive = {}, onTapVod = {}, onTapSeries = {})
                }
            }
            compose.runOnIdle { repository.loadAll() }
            compose.waitUntil(15000) { repository.vodStreams.size == 141 && !repository.vodLoading }
            compose.onNodeWithText("Categoría uno (140)").performClick()
            compose.waitUntil(10000) {
                compose.onAllNodesWithText("140 títulos").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("catalog_grid").performScrollToIndex(139)
            compose.onNodeWithText("Película número 139").assertIsDisplayed()

            compose.onNodeWithContentDescription("Buscar").performClick()
            compose.onNodeWithTag("catalog_search").performTextInput("senor anillos")
            compose.onNodeWithTag("catalog_search").performImeAction()
            compose.waitUntil(15000) {
                compose.onAllNodesWithText("El Señor de los Anillos").fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitUntil(15000) {
                try {
                    compose.onNodeWithText("El Señor de los Anillos").assertIsDisplayed()
                    true
                } catch (_: AssertionError) { false }
            }
            compose.onNodeWithText("En todas las películas", substring = true).assertExists()
            compose.onNodeWithTag("catalog_search").performTextReplacement("noexiste987")
            compose.onNodeWithTag("catalog_search").performTextReplacement("ANILLOS")
            compose.onNodeWithTag("catalog_search").performImeAction()
            compose.waitUntil(10000) {
                compose.onAllNodesWithText("1 título").fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitUntil(15000) {
                try {
                    compose.onNodeWithText("El Señor de los Anillos").assertIsDisplayed()
                    true
                } catch (_: AssertionError) { false }
            }
            compose.onNodeWithContentDescription("Cerrar búsqueda").performClick()
            compose.waitUntil(10000) {
                compose.onAllNodesWithText("140 títulos").fetchSemanticsNodes().isNotEmpty()
            }
        } finally { compose.runOnIdle { repository.cancel() } }
    }
}
