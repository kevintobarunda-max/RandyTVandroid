package com.randytv.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randytv.mobile.data.catalog.*
import com.randytv.mobile.data.model.*
import com.randytv.mobile.data.repository.DataRepository
import com.randytv.mobile.ui.components.CachedImage
import com.randytv.mobile.ui.components.FocusGreen
import com.randytv.mobile.ui.components.tvFocusable
import kotlinx.coroutines.delay

enum class ListType { LIVE, VOD, SERIES }

// Kept for the live zapping route; no hidden title/word exclusions.
fun is2MBChannel(stream: LiveStream): Boolean = ChannelGroups.isLowBitrate(stream)
fun get2MBList(streams: List<LiveStream>): List<LiveStream> =
    streams.filter(ChannelGroups::isLowBitrate).sortedBy { CatalogText.normalize(it.name) }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ListScreen(type: ListType, showSimpsons: Boolean = false, repository: DataRepository,
    onHome: () -> Unit, onTapLive: (LiveStream) -> Unit, onTapVod: (VodStream) -> Unit,
    onTapSeries: (SeriesItem) -> Unit) {
    var selectedCat by rememberSaveable(type, showSimpsons) {
        mutableStateOf(if (showSimpsons) SIMPSONS else if (type == ListType.LIVE) LOW_BITRATE else ALL_CATEGORIES)
    }
    var searchText by rememberSaveable(type, showSimpsons) { mutableStateOf("") }
    var searchOpen by rememberSaveable(type, showSimpsons) { mutableStateOf(false) }
    var requestSearchFocus by remember { mutableStateOf(false) }
    var restoreSearchFocus by remember { mutableStateOf(false) }
    var requestResultsFocus by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val searchButtonFocus = remember { FocusRequester() }
    val firstResultFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(searchOpen, requestSearchFocus, restoreSearchFocus) {
        if (searchOpen && requestSearchFocus) {
            withFrameNanos { }
            fieldFocus.requestFocus()
            keyboard?.show()
            requestSearchFocus = false
        } else if (!searchOpen && restoreSearchFocus) {
            withFrameNanos { }
            searchButtonFocus.requestFocus()
            restoreSearchFocus = false
        }
    }

    val onCategory: (String) -> Unit = {
        selectedCat = it
        searchText = ""
        requestResultsFocus = false
        keyboard?.hide()
    }
    Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        Row(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color(0xFF111111))
            .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onHome) { Icon(Icons.Default.Home, "Inicio", tint = FocusGreen) }
            Text(if (showSimpsons) "SIMPSONS" else when (type) {
                ListType.LIVE -> "TV EN VIVO"; ListType.VOD -> "PELÍCULAS"; ListType.SERIES -> "SERIES"
            }, color = FocusGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (searchOpen) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it; requestResultsFocus = false },
                    placeholder = { Text("Buscar por nombre", fontSize = 13.sp, color = androidx.compose.ui.graphics.Color.LightGray) },
                    singleLine = true,
                    modifier = Modifier.widthIn(max = 300.dp).heightIn(min = 56.dp)
                        .focusRequester(fieldFocus).testTag("catalog_search"),
                    textStyle = LocalTextStyle.current.copy(color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrect = false),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboard?.hide()
                        requestResultsFocus = true
                    }),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = FocusGreen, cursorColor = FocusGreen,
                        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Gray,
                        focusedTextColor = androidx.compose.ui.graphics.Color.White,
                        unfocusedTextColor = androidx.compose.ui.graphics.Color.White)
                )
                IconButton(onClick = {
                    searchText = ""; searchOpen = false
                    requestResultsFocus = false; restoreSearchFocus = true
                    keyboard?.hide()
                }) { Icon(Icons.Default.Close, "Cerrar búsqueda", tint = androidx.compose.ui.graphics.Color.White) }
            } else {
                IconButton(onClick = { searchOpen = true; requestSearchFocus = true },
                    modifier = Modifier.focusRequester(searchButtonFocus)) {
                    Icon(Icons.Default.Search, "Buscar", tint = FocusGreen)
                }
            }
        }

        when (type) {
            ListType.LIVE -> CatalogSection(
                index = repository.liveIndex, categories = repository.liveCategories,
                selectedCat = selectedCat, onCategory = onCategory, query = searchText,
                loading = repository.liveLoading, error = repository.liveError,
                onRetry = { repository.reloadLive() }, kind = if (showSimpsons) "Simpsons" else "canales",
                extras = if (showSimpsons) listOf(Category(SIMPSONS, "Simpsons"))
                    else listOf(Category(LOW_BITRATE, "2MB"), Category(ROUND_THE_CLOCK, "24 Horas")),
                onlyExtras = showSimpsons, searchAll = !showSimpsons, square = true,
                title = { it.name }, image = { it.streamIcon }, rating = { null }, onTap = onTapLive,
                firstResultFocus = firstResultFocus, requestResultsFocus = requestResultsFocus,
                onResultsFocused = { requestResultsFocus = false }, modifier = Modifier.weight(1f))
            ListType.VOD -> CatalogSection(
                index = repository.vodIndex, categories = repository.vodCategories,
                selectedCat = selectedCat, onCategory = onCategory, query = searchText,
                loading = repository.vodLoading, error = repository.vodError,
                onRetry = { repository.reloadVod() }, kind = "películas",
                title = { it.name }, image = { it.getImageUrl() }, rating = { it.getRating() }, onTap = onTapVod,
                firstResultFocus = firstResultFocus, requestResultsFocus = requestResultsFocus,
                onResultsFocused = { requestResultsFocus = false }, modifier = Modifier.weight(1f))
            ListType.SERIES -> CatalogSection(
                index = repository.seriesIndex, categories = repository.seriesCategories,
                selectedCat = selectedCat, onCategory = onCategory, query = searchText,
                loading = repository.seriesLoading, error = repository.seriesError,
                onRetry = { repository.reloadSeries() }, kind = "series",
                title = { it.name }, image = { it.getImageUrl() }, rating = { it.getRating() }, onTap = onTapSeries,
                firstResultFocus = firstResultFocus, requestResultsFocus = requestResultsFocus,
                onResultsFocused = { requestResultsFocus = false }, modifier = Modifier.weight(1f))
        }
    }
}

@Stable
private data class CatalogResult<T>(
    val source: CatalogIndex<T>? = null, val category: String = "", val query: String = "",
    val rows: List<CatalogEntry<T>> = emptyList()
)

@Composable
private fun <T> CatalogSection(
    index: CatalogIndex<T>, categories: List<Category>, selectedCat: String,
    onCategory: (String) -> Unit, query: String, loading: Boolean, error: String?,
    onRetry: () -> Unit, kind: String, title: (T) -> String?, image: (T) -> String?,
    rating: (T) -> Double?, onTap: (T) -> Unit, firstResultFocus: FocusRequester,
    requestResultsFocus: Boolean, onResultsFocused: () -> Unit, modifier: Modifier,
    extras: List<Category> = emptyList(), onlyExtras: Boolean = false,
    searchAll: Boolean = true, square: Boolean = false
) {
    val mergedCategories = remember(index, categories) { index.categories(categories) }
    LaunchedEffect(index, mergedCategories, selectedCat, loading) {
        if (!loading && selectedCat != ALL_CATEGORIES &&
            extras.none { it.categoryId == selectedCat } &&
            mergedCategories.none { it.categoryId == selectedCat }) onCategory(ALL_CATEGORIES)
    }
    val result by produceState(CatalogResult<T>(), index, selectedCat, query, searchAll) {
        val cached = index.immediate(selectedCat, query, searchAll)
        if (cached != null) {
            value = CatalogResult(index, selectedCat, query, cached)
        } else {
            // One frame coalesces key repeats; cached searches/categories have no delay.
            delay(16)
            value = CatalogResult(index, selectedCat, query, index.query(selectedCat, query, searchAll))
        }
    }
    val busy = result.source !== index || result.category != selectedCat || result.query != query
    val gridState = rememberLazyGridState()
    var lastFilter by rememberSaveable { mutableStateOf(selectedCat + "|" + query) }
    LaunchedEffect(result, busy) {
        val filter = selectedCat + "|" + query
        if (!busy && lastFilter != filter) {
            gridState.scrollToItem(0)
            lastFilter = filter
        }
    }
    LaunchedEffect(requestResultsFocus, result, busy) {
        if (requestResultsFocus && !busy && result.rows.isNotEmpty()) {
            gridState.scrollToItem(0)
            withFrameNanos { }
            firstResultFocus.requestFocus()
            onResultsFocused()
        }
    }

    Column(modifier.fillMaxWidth()) {
        LazyRow(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color(0xFF0A0A0A)),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!onlyExtras) item(key = ALL_CATEGORIES) {
                TVCatChip("Todos (${index.entries.size})", (selectedCat == ALL_CATEGORIES || (query.isNotBlank() && searchAll))) { onCategory(ALL_CATEGORIES) }
            }
            items(extras, key = { it.categoryId }) { category ->
                TVCatChip("${category.categoryName} (${index.count(category.categoryId)})",
                    (selectedCat == category.categoryId && (query.isBlank() || !searchAll))) { onCategory(category.categoryId) }
            }
            if (!onlyExtras) items(mergedCategories, key = { it.categoryId }) { category ->
                TVCatChip("${category.categoryName} (${index.count(category.categoryId)})",
                    (selectedCat == category.categoryId && (query.isBlank() || !searchAll))) { onCategory(category.categoryId) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(if (busy) "Buscando…" else if (result.rows.size == 1) "1 título" else "${result.rows.size} títulos",
                color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp)
            if (query.isNotBlank() && searchAll) {
                Text(if (kind == "canales") " · En todos los canales" else " · En todas las $kind", color = FocusGreen, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onRetry, enabled = !loading) { Text("Actualizar", color = FocusGreen) }
        }
        if (loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp), color = FocusGreen)
            Text("Cargando $kind… ${index.entries.size} títulos disponibles",
                modifier = Modifier.padding(horizontal = 14.dp), fontSize = 11.sp,
                color = androidx.compose.ui.graphics.Color.LightGray)
        } else if (busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp), color = FocusGreen)
        }
        error?.let {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(it, color = androidx.compose.ui.graphics.Color(0xFFFFD180), fontSize = 12.sp,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = onRetry) { Text("Reintentar", color = FocusGreen) }
            }
        }
        if (!busy && !loading && result.rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (error != null) "No hay títulos descargados. Pulsa Reintentar."
                    else if (query.isNotBlank()) "No se encontraron títulos con ese nombre."
                    else "El servidor no devolvió títulos para esta selección.",
                    color = androidx.compose.ui.graphics.Color.LightGray)
            }
        } else {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                // Reserve space for captions and padding even on short landscape windows.
                val artworkHeight = (maxHeight - 64.dp).coerceIn(56.dp, if (square) 80.dp else 138.dp)
                CatalogGrid(result, gridState, square, artworkHeight, firstResultFocus,
                    title, image, rating, onTap)
            }
        }
    }
}

// Stable result identity lets Compose skip all visible cards while only the query changes.
@Composable
private fun <T> CatalogGrid(result: CatalogResult<T>, gridState: LazyGridState,
    square: Boolean, artworkHeight: Dp, firstResultFocus: FocusRequester,
    title: (T) -> String?, image: (T) -> String?, rating: (T) -> Double?, onTap: (T) -> Unit
) {
                LazyVerticalGrid(columns = GridCells.Adaptive(if (square) 96.dp else 112.dp),
                    state = gridState, modifier = Modifier.fillMaxSize().testTag("catalog_grid"),
                    contentPadding = PaddingValues(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(result.rows, key = { it.rowKey }, contentType = { if (square) "channel" else "poster" }) { entry ->
                        TVCard(title(entry.item).orEmpty().ifBlank { "Título sin nombre" },
                            image(entry.item).orEmpty(), square, rating(entry.item), artworkHeight = artworkHeight,
                            modifier = if (entry.rowKey == result.rows.firstOrNull()?.rowKey)
                                Modifier.focusRequester(firstResultFocus) else Modifier) { onTap(entry.item) }
                    }
                }
}

@Composable
private fun TVCatChip(name: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(Modifier.clip(RoundedCornerShape(8.dp))
        .background(when { selected -> FocusGreen; focused -> androidx.compose.ui.graphics.Color(0xFF444444)
            else -> androidx.compose.ui.graphics.Color(0xFF202020) })
        .tvFocusable { focused = it }.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(name, fontSize = 12.sp, color = if (selected) androidx.compose.ui.graphics.Color.Black
            else androidx.compose.ui.graphics.Color.White, maxLines = 1)
    }
}

@Composable
private fun TVCard(name: String, imageUrl: String, square: Boolean, rating: Double?,
    modifier: Modifier = Modifier, artworkHeight: Dp = if (square) 80.dp else 138.dp, onClick: () -> Unit) {
    val ratingLabel = remember(rating) { if (rating != null && rating > 0) String.format("%.1f", rating) else null }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier.width(if (square) artworkHeight else artworkHeight * (2f / 3f)).height(artworkHeight)
            .clip(RoundedCornerShape(8.dp)).background(androidx.compose.ui.graphics.Color(0xFF1E1E1E))
            .semantics { contentDescription = name }.tvFocusable().clickable(onClick = onClick)) {
            CachedImage(imageUrl, height = artworkHeight,
                modifier = Modifier.fillMaxSize(),
                contentScale = if (square) ContentScale.Fit else ContentScale.Crop,
                targetWidthPx = 160, targetHeightPx = if (square) 160 else 240)
            if (ratingLabel != null) {
                Text(ratingLabel, color = androidx.compose.ui.graphics.Color.Black,
                    fontSize = 9.sp, modifier = Modifier.align(Alignment.TopEnd).padding(3.dp)
                        .background(FocusGreen, RoundedCornerShape(3.dp)).padding(horizontal = 3.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(name, color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp, maxLines = 2,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.width(if (square) 80.dp else 100.dp))
    }
}
