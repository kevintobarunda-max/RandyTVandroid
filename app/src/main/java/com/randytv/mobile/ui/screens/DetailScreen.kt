package com.randytv.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randytv.mobile.data.model.Episode
import com.randytv.mobile.data.model.SeriesItem
import com.randytv.mobile.data.model.VodStream
import com.randytv.mobile.data.network.OmdbResult
import com.randytv.mobile.data.network.OmdbService
import com.randytv.mobile.data.repository.DataRepository
import com.randytv.mobile.ui.components.CachedImage
import com.randytv.mobile.ui.components.FocusGreen
import com.randytv.mobile.ui.components.tvFocusable
import kotlinx.coroutines.launch

@Composable
fun VodDetailScreen(stream: VodStream, onBack: () -> Unit, onPlay: (VodStream) -> Unit) {
    val playFocus = remember { FocusRequester() }
    var omdb by remember { mutableStateOf<OmdbResult?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { playFocus.requestFocus(); scope.launch { omdb = OmdbService().searchByTitle(stream.name) } }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF0A0A0A)).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Icon(Icons.Default.ChevronLeft, null, tint = FocusGreen, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Volver", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FocusGreen) }
        }
        Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CachedImage(url = stream.getImageUrl(), height = 240.dp, modifier = Modifier.width(160.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.height(10.dp))
                Button(onClick = { onPlay(stream) }, modifier = Modifier.width(160.dp).height(44.dp).focusRequester(playFocus).tvFocusable(), colors = ButtonDefaults.buttonColors(containerColor = FocusGreen), shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("Reproducir", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.width(24.dp))
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(stream.name, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    val r = omdb?.imdbRating ?: stream.getRating()?.let { String.format("%.1f", it) }
                    if (r != null) InfoBadge(Icons.Default.Star, "IMDb $r/10", Color.Yellow)
                    omdb?.imdbVotes?.let { InfoBadge(Icons.Default.People, "$it votos", Color(0xFF90CAF9)) }
                    val y = omdb?.year ?: stream.releaseDate?.take(4)
                    if (!y.isNullOrEmpty()) InfoBadge(Icons.Default.CalendarMonth, y, Color(0xFFB0B0B0))
                    omdb?.runtime?.let { InfoBadge(Icons.Default.Timer, it, Color(0xFFB0B0B0)) }
                }
                Spacer(Modifier.height(6.dp))
                val genre = omdb?.genre ?: stream.genre
                if (!genre.isNullOrEmpty()) { Text(genre, fontSize = 11.sp, color = Color(0xFFB0B0B0)); Spacer(Modifier.height(4.dp)) }
                val dir = omdb?.director ?: stream.director
                if (!dir.isNullOrEmpty()) { Text("Director: $dir", fontSize = 11.sp, color = Color(0xFF90CAF9)); Spacer(Modifier.height(8.dp)) }
                omdb?.awards?.let { if (it.isNotEmpty()) { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(Color(0xFF1A1A00), RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) { Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(it, fontSize = 11.sp, color = Color(0xFFFFD700)) }; Spacer(Modifier.height(10.dp)) } }
                val plot = omdb?.plot ?: stream.plot
                if (!plot.isNullOrEmpty()) { Text("Resena", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FocusGreen); Spacer(Modifier.height(4.dp)); Text(plot, fontSize = 12.sp, color = Color(0xCCFFFFFF), lineHeight = 18.sp); Spacer(Modifier.height(12.dp)) }
                val actors = omdb?.actors ?: stream.cast
                val list = (actors ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (list.isNotEmpty()) { Text("Elenco", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FocusGreen); Spacer(Modifier.height(6.dp)); Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) { list.take(10).forEach { CastChip(it) } } }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun SeriesDetailScreen(series: SeriesItem, repository: DataRepository, onBack: () -> Unit, onPlayEpisode: (Episode) -> Unit) {
    var allEpisodes by remember { mutableStateOf<Map<Int, List<Episode>>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var selectedSeason by remember { mutableIntStateOf(1) }
    var omdb by remember { mutableStateOf<OmdbResult?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(series.seriesId) {
        scope.launch { omdb = OmdbService().searchByTitle(series.name) }
        scope.launch { val info = repository.loadSeriesInfo(series.seriesId.toString()); info?.episodes?.let { m -> val g = mutableMapOf<Int, List<Episode>>(); m.forEach { (k, eps) -> g[k.toIntOrNull() ?: 1] = eps.sortedBy { it.episodeNum } }; allEpisodes = g.toSortedMap(); if (g.isNotEmpty()) selectedSeason = g.keys.first() }; loading = false }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF0A0A0A)).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Icon(Icons.Default.ChevronLeft, null, tint = FocusGreen, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Volver", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FocusGreen) }
        }
        Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(160.dp)) {
                CachedImage(url = series.getImageUrl(), height = 200.dp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.height(6.dp))
                val r = omdb?.imdbRating ?: series.getRating()?.let { String.format("%.1f", it) }
                if (r != null) { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(Color(0xFF1A1A1A), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) { Icon(Icons.Default.Star, null, tint = Color.Yellow, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp)); Text("IMDb $r", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White) } }
                omdb?.imdbVotes?.let { Text("$it votos", fontSize = 8.sp, color = Color(0xFF90CAF9)) }
                omdb?.awards?.let { if (it.isNotEmpty()) { Spacer(Modifier.height(4.dp)); Text(it, fontSize = 8.sp, color = Color(0xFFFFD700), textAlign = TextAlign.Center) } }
            }
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(series.name, fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
                Spacer(Modifier.height(6.dp))
                val genre = omdb?.genre ?: series.genre
                if (!genre.isNullOrEmpty()) { Text(genre, fontSize = 10.sp, color = Color(0xFFB0B0B0)); Spacer(Modifier.height(4.dp)) }
                val dir = omdb?.director ?: series.director
                if (!dir.isNullOrEmpty()) { Text("Director: $dir", fontSize = 10.sp, color = Color(0xFF90CAF9)); Spacer(Modifier.height(6.dp)) }
                val plot = omdb?.plot ?: series.plot
                if (!plot.isNullOrEmpty()) { Text("Resena", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FocusGreen); Spacer(Modifier.height(4.dp)); Text(plot, fontSize = 11.sp, color = Color(0xCCFFFFFF), lineHeight = 16.sp); Spacer(Modifier.height(10.dp)) }
                if (allEpisodes.size > 1) { Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { allEpisodes.keys.forEach { s -> SeasonChip("T$s", selectedSeason == s) { selectedSeason = s } } }; Spacer(Modifier.height(8.dp)) } else if (allEpisodes.isNotEmpty()) { Text("Temporada ${allEpisodes.keys.first()}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FocusGreen); Spacer(Modifier.height(6.dp)) }
                if (loading) { CircularProgressIndicator(color = FocusGreen, modifier = Modifier.size(24.dp)) } else { val eps = allEpisodes[selectedSeason] ?: emptyList(); Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) { eps.forEach { ep -> EpCard(ep) { onPlayEpisode(ep) } } } }
                Spacer(Modifier.height(12.dp))
                val actors = omdb?.actors ?: series.cast
                val list = (actors ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (list.isNotEmpty()) { Text("Elenco", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FocusGreen); Spacer(Modifier.height(6.dp)); Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) { list.take(10).forEach { CastChip(it) } } }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable private fun InfoBadge(icon: ImageVector, text: String, color: Color) { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(Color(0xFF1A1A1A), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) { Icon(icon, null, tint = color, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp)); Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White) } }

@Composable private fun SeasonChip(label: String, selected: Boolean, onClick: () -> Unit) { var f by remember { mutableStateOf(false) }; Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(when { selected -> FocusGreen; f -> Color(0x55FFFFFF); else -> Color(0x1AFFFFFF) }).tvFocusable { f = it }.clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp)) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.Black else Color.White) } }

@Composable private fun EpCard(episode: Episode, onClick: () -> Unit) { val img = episode.info?.movieImage ?: ""; Column(modifier = Modifier.width(150.dp).tvFocusable().clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) { Box(modifier = Modifier.fillMaxWidth().height(85.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1A1A1A)), contentAlignment = Alignment.Center) { if (img.isNotEmpty()) CachedImage(url = img, height = 85.dp, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()); Box(modifier = Modifier.size(32.dp).background(Color(0x99000000), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp)) } }; Spacer(Modifier.height(3.dp)); Text("E${episode.episodeNum} - ${episode.title.ifEmpty { "Episodio ${episode.episodeNum}" }}", fontSize = 9.sp, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) } }

@Composable private fun CastChip(name: String) { Row(modifier = Modifier.background(Color(0x1AFFFFFF), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(22.dp).background(FocusGreen.copy(alpha = 0.6f), CircleShape), contentAlignment = Alignment.Center) { Text(name.split(" ").let { if (it.size >= 2) "${it[0].first()}${it[1].first()}" else name.take(2) }.uppercase(), fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.Black) }; Spacer(Modifier.width(6.dp)); Text(name, fontSize = 10.sp, color = Color.White, maxLines = 1) } }
