package com.randytv.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randytv.mobile.data.model.*
import com.randytv.mobile.data.repository.DataRepository
import com.randytv.mobile.ui.components.CachedImage
import com.randytv.mobile.ui.components.FocusGreen
import com.randytv.mobile.ui.components.tvFocusable

enum class ListType { LIVE, VOD, SERIES }

private val adultKeywords = listOf("xxx", "adult", "porn", "erotic", "playboy", "hustle", "penthouse", "brazzers", "18+", "sexy", "venus", "desnud", "vivid")
private fun isAdult(name: String): Boolean { val l = name.lowercase(); return adultKeywords.any { l.contains(it) } }
fun is2MBChannel(stream: LiveStream): Boolean { val l = stream.name.lowercase(); return l.contains("2mb") || l.contains("| 2") || l.contains("|2") || l.contains(" 2 mb") || l.contains("[2]") || l.contains("(2)") || l.contains("2 mb") || l.contains("sd|") || l.contains("|sd") || l.contains(" sd ") }
fun get2MBList(streams: List<LiveStream>): List<LiveStream> = streams.filter { !isAdult(it.name) && is2MBChannel(it) }.sortedBy { it.name.lowercase() }

private fun is24HChannel(stream: LiveStream): Boolean {
    val l = stream.name.lowercase()
    return l.contains("24") || l.contains("24h") || l.contains("24/7") || l.contains("24 h")
}

@Composable
fun ListScreen(type: ListType, showSimpsons: Boolean = false, repository: DataRepository,
    onHome: () -> Unit, onTapLive: (LiveStream) -> Unit, onTapVod: (VodStream) -> Unit, onTapSeries: (SeriesItem) -> Unit) {
    var selectedCat by remember { mutableStateOf("2mb") }
    var searchText by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Top bar
        Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF111111)).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onHome, modifier = Modifier.size(36.dp).tvFocusable()) { Icon(Icons.Default.Home, "Home", tint = FocusGreen, modifier = Modifier.size(20.dp)) }
            Spacer(modifier = Modifier.width(10.dp))
            Text(when { showSimpsons -> "SIMPSONS"; type == ListType.LIVE -> "TV EN VIVO"; type == ListType.VOD -> "PELICULAS"; else -> "SERIES" }, fontSize = 14.sp, fontWeight = FontWeight.Black, color = if (showSimpsons) Color.Yellow else FocusGreen)
            Spacer(modifier = Modifier.weight(1f))

            // Buscar - solo se abre si se selecciona
            if (searchOpen) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    placeholder = { Text("...", fontSize = 11.sp, color = Color.Gray) },
                    modifier = Modifier.width(180.dp).height(36.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, color = Color.White),
                    singleLine = true,
                    shape = RoundedCornerShape(6.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = FocusGreen, unfocusedBorderColor = Color(0x33FFFFFF), focusedContainerColor = Color(0x1AFFFFFF), unfocusedContainerColor = Color(0x0DFFFFFF))
                )
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = { searchOpen = false; searchText = "" }, modifier = Modifier.size(28.dp)) {
                    Text("X", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            } else {
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0x0DFFFFFF))
                        .clickable { searchOpen = true }
                        .tvFocusable { if (it) searchOpen = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Buscar", fontSize = 10.sp, color = Color.Gray)
                    }
                }
            }
        }

        if (showSimpsons) {
            // Memoizado: la lista de Simpsons no cambia salvo que cambie el repositorio
            val simpsonsFiltered = remember(repository.simpsonsChannels) {
                repository.simpsonsChannels.filter { !isAdult(it.name) }
            }
            LazyVerticalGrid(columns = GridCells.Fixed(6), contentPadding = PaddingValues(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                items(simpsonsFiltered) { ch -> TVCard(ch.name, ch.streamIcon, true) { onTapLive(ch) } }
            }
        } else if (type == ListType.LIVE) {
            // Categorias: memoizadas, solo se recalculan si cambia la lista base del repo
            val categories = remember(repository.liveCategories) {
                repository.liveCategories.filter { cat -> !adultKeywords.any { kw -> cat.categoryName.lowercase().contains(kw) } }
            }
            Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF0A0A0A)).horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TVCatChip("2MB", selectedCat == "2mb") { selectedCat = "2mb" }
                TVCatChip("24 Horas", selectedCat == "24h") { selectedCat = "24h" }
                categories.forEach { cat -> TVCatChip(cat.categoryName, selectedCat == cat.categoryId) { selectedCat = cat.categoryId } }
            }
            // Memoizado: filtrar+ordenar la lista completa de canales solo cuando
            // cambia la categoria seleccionada o la lista base del repo (no en cada
            // recomposicion / cada letra escrita en el buscador)
            val currentList = remember(selectedCat, repository.liveStreams) {
                when (selectedCat) {
                    "2mb" -> get2MBList(repository.liveStreams)
                    "24h" -> repository.liveStreams.filter { !isAdult(it.name) && is24HChannel(it) }.sortedBy { it.name.lowercase() }
                    else -> repository.liveStreams.filter { it.categoryId == selectedCat && !isAdult(it.name) }
                }
            }
            // Solo el filtro de texto (mas liviano) se recalcula al escribir
            val searchFiltered = remember(currentList, searchText) {
                if (searchText.isNotEmpty()) currentList.filter { it.name.contains(searchText, true) } else currentList
            }
            LazyVerticalGrid(columns = GridCells.Fixed(7), contentPadding = PaddingValues(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                items(searchFiltered.take(150)) { s -> TVCard(s.name, s.streamIcon, true) { onTapLive(s) } }
            }
        } else {
            // VOD y Series - categorias navegables con control
            val categories = remember(type, repository.vodCategories, repository.seriesCategories) {
                when (type) { ListType.VOD -> repository.vodCategories; else -> repository.seriesCategories }
                    .filter { cat -> !adultKeywords.any { kw -> cat.categoryName.lowercase().contains(kw) } }
            }
            if (selectedCat == "2mb" || selectedCat == "24h") selectedCat = categories.firstOrNull()?.categoryId ?: ""
            Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF0A0A0A)).horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.forEach { cat -> TVCatChip(cat.categoryName, selectedCat == cat.categoryId) { selectedCat = cat.categoryId } }
            }
            when (type) {
                ListType.VOD -> {
                    val f = remember(repository.vodStreams, selectedCat, searchText) {
                        filterVod(repository.vodStreams, selectedCat, searchText)
                    }
                    LazyVerticalGrid(columns = GridCells.Fixed(6), contentPadding = PaddingValues(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        items(f) { s -> TVCard(s.name, s.getImageUrl(), false, s.getRating()) { onTapVod(s) } }
                    }
                }
                else -> {
                    val f = remember(repository.seriesList, selectedCat, searchText) {
                        filterSeries(repository.seriesList, selectedCat, searchText)
                    }
                    LazyVerticalGrid(columns = GridCells.Fixed(6), contentPadding = PaddingValues(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        items(f) { s -> TVCard(s.name, s.getImageUrl(), false, s.getRating()) { onTapSeries(s) } }
                    }
                }
            }
        }
    }
}

@Composable private fun TVCatChip(name: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(modifier = Modifier.clip(RoundedCornerShape(12.dp))
        .background(when { selected -> FocusGreen; focused -> Color(0x55FFFFFF); else -> Color(0x0DFFFFFF) })
        .tvFocusable { focused = it }
        .clickable(onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(name, fontSize = 11.sp, fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium, color = if (selected) Color.Black else Color.White, maxLines = 1)
    }
}

@Composable private fun TVCard(name: String, imageUrl: String, isSquare: Boolean, rating: Double? = null, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier
            .then(if (isSquare) Modifier.size(80.dp) else Modifier.width(100.dp).aspectRatio(2f / 3f))
            .clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E1E1E))
            .tvFocusable().clickable(onClick = onClick)
        ) {
            // Tamano de decodificacion ajustado al tamano real de la tarjeta:
            // cuadrados (canales) piden imagenes ~2x mas chicas que antes,
            // reduciendo el trabajo de Coil/CPU al pintar la grilla
            if (isSquare) {
                CachedImage(url = imageUrl, height = 999.dp, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(), targetWidthPx = 160, targetHeightPx = 160)
            } else {
                CachedImage(url = imageUrl, height = 999.dp, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), targetWidthPx = 200, targetHeightPx = 300)
            }
            rating?.let { if (it > 0) { Text(String.format("%.1f", it), fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.Black, modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).background(FocusGreen, RoundedCornerShape(2.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) } }
        }
        Spacer(Modifier.height(4.dp))
        Text(name, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.width(if (isSquare) 80.dp else 100.dp))
    }
}

private fun filterVod(streams: List<VodStream>, cat: String, search: String): List<VodStream> { var r = streams.filter { !isAdult(it.name) }; if (cat.isNotEmpty()) r = r.filter { it.categoryId == cat }; if (search.isNotEmpty()) r = r.filter { it.name.contains(search, true) }; return r.sortedByDescending { it.getRating() ?: 0.0 }.take(100) }
private fun filterSeries(series: List<SeriesItem>, cat: String, search: String): List<SeriesItem> { var r = series.filter { !isAdult(it.name) }; if (cat.isNotEmpty()) r = r.filter { it.categoryId == cat }; if (search.isNotEmpty()) r = r.filter { it.name.contains(search, true) }; return r.sortedByDescending { it.getRating() ?: 0.0 }.take(100) }
