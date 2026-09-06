package com.randytv.mobile.ui.screens

import android.view.KeyEvent
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.randytv.mobile.player.PlayerController
import com.randytv.mobile.ui.components.CachedImage
import com.randytv.mobile.ui.components.FocusGreen
import com.randytv.mobile.ui.components.FocusGlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(playerController: PlayerController, onClose: () -> Unit) {
    var uiVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var hideJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val mainFocus = remember { FocusRequester() }
    val firstBtnFocus = remember { FocusRequester() }
    var inBarMode by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var bufferPercent by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(playerController.isPlaying) { while (playerController.isPlaying && !playerController.isLive) { playerController.updateProgress(); delay(500) } }
    LaunchedEffect(Unit) { if (!playerController.isLive) { isBuffering = true; bufferPercent = 0f; var w = 0; while (playerController.activePlayer.playbackState != androidx.media3.common.Player.STATE_READY && w < 10000) { delay(100); w += 100; bufferPercent = (w / 10000f).coerceAtMost(0.95f) }; bufferPercent = 1f; delay(300); isBuffering = false } else isBuffering = false }
    LaunchedEffect(Unit) { mainFocus.requestFocus(); repeat(10) { if (!playerController.activePlayer.isPlaying) { playerController.activePlayer.playWhenReady = true; playerController.activePlayer.play() }; delay(200) } }

    fun showUI() { uiVisible = true; hideJob?.cancel(); hideJob = scope.launch { delay(if (inBarMode) 20000 else 4000); uiVisible = false; inBarMode = false } }
    LaunchedEffect(Unit) { showUI() }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { ctx -> SurfaceView(ctx).also { playerController.activePlayer.setVideoSurfaceView(it) } }, update = { sv -> playerController.activePlayer.setVideoSurfaceView(sv) }, modifier = Modifier.fillMaxSize())

        // Zona principal
        Box(modifier = Modifier.fillMaxSize()
            .then(if (!inBarMode) Modifier.focusRequester(mainFocus).focusable() else Modifier)
            .onKeyEvent { event ->
                if (inBarMode) return@onKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent true
                when (event.key.nativeKeyCode) {
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { if (playerController.isLive) { playerController.zapNext(); showUI() }; true }
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { if (playerController.isLive) { playerController.zapPrev(); showUI() } else { inBarMode = true; uiVisible = true; hideJob?.cancel(); scope.launch { delay(150); firstBtnFocus.requestFocus() } }; true }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> { if (!playerController.isLive) playerController.jumpBackward(15); showUI(); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { if (!playerController.isLive) playerController.jumpForward(30); showUI(); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { if (!playerController.isLive) playerController.togglePause(); showUI(); true }
                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { onClose(); true }
                    else -> { showUI(); true }
                }
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { showUI() }) }
            .pointerInput(Unit) { detectDragGestures { _, d -> if (playerController.isLive) { if (d.y < -50) { playerController.zapNext(); showUI() } else if (d.y > 50) { playerController.zapPrev(); showUI() } else if (d.x > 80) onClose() } else { if (d.x > 80) onClose() } } }
        )

        if (playerController.isTuning) { Box(modifier = Modifier.align(Alignment.TopEnd).padding(20.dp).size(50.dp).background(Color(0xCC000000), CircleShape), contentAlignment = Alignment.Center) { CircularProgressIndicator(progress = playerController.tuningProgress, modifier = Modifier.size(38.dp), color = FocusGlow, trackColor = Color(0x33FFFFFF), strokeWidth = 3.dp, strokeCap = StrokeCap.Round); Text("${(playerController.tuningProgress * 100).toInt()}%", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White) } }
        if (!playerController.isLive && isBuffering) { Box(modifier = Modifier.align(Alignment.Center).size(70.dp).background(Color(0xCC000000), CircleShape), contentAlignment = Alignment.Center) { CircularProgressIndicator(progress = bufferPercent, modifier = Modifier.size(54.dp), color = FocusGlow, trackColor = Color(0x33FFFFFF), strokeWidth = 4.dp, strokeCap = StrokeCap.Round); Text("${(bufferPercent * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White) } }

        if (uiVisible) {
            Row(modifier = Modifier.align(Alignment.TopStart).padding(16.dp).background(Color(0xAA000000), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { if (playerController.logo.isNotEmpty()) { CachedImage(url = playerController.logo, height = 32.dp, modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp))); Spacer(Modifier.width(8.dp)) }; Column { Text(playerController.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp)); if (playerController.isLive) Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(5.dp).background(Color.Red, CircleShape)); Spacer(Modifier.width(4.dp)); Text("EN VIVO", fontSize = 9.sp, color = Color.Red, fontWeight = FontWeight.Bold) } } }
            if (!playerController.isTuning) { Box(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(36.dp).background(Color(0x80000000), CircleShape).clickable { onClose() }, contentAlignment = Alignment.Center) { Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.size(18.dp)) } }

            if (!playerController.isLive) {
                Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xDD000000)).padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Box(modifier = Modifier.fillMaxWidth().height(20.dp).pointerInput(Unit) { detectTapGestures { offset -> playerController.seek((offset.x / size.width).coerceIn(0f, 1f)) } }, contentAlignment = Alignment.CenterStart) { Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x33FFFFFF))); Box(modifier = Modifier.fillMaxWidth(playerController.progress.coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(FocusGreen)) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(playerController.timeStr, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0x99FFFFFF)); Text(playerController.durationStr, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0x99FFFFFF)) }
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        GreenBtn(Icons.Default.Replay10, Modifier.focusRequester(firstBtnFocus)) { playerController.jumpBackward(15) }
                        Spacer(Modifier.width(6.dp))
                        GreenBtn(if (playerController.activePlayer.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow) { playerController.togglePause() }
                        Spacer(Modifier.width(6.dp))
                        GreenBtn(Icons.Default.Forward30) { playerController.jumpForward(30) }
                        Spacer(Modifier.weight(1f))
                        var sa by remember { mutableStateOf(false) }
                        Box { GreenBtn(Icons.Default.AspectRatio) { sa = true }; DropdownMenu(expanded = sa, onDismissRequest = { sa = false }) { playerController.aspectModes.forEachIndexed { i, m -> DropdownMenuItem(text = { Text(m + if (i == playerController.aspectMode) " *" else "") }, onClick = { playerController.cycleAspect(); sa = false }) } } }
                        Spacer(Modifier.width(6.dp))
                        var aa by remember { mutableStateOf(false) }
                        Box { GreenBtn(Icons.Default.VolumeUp) { aa = true }; DropdownMenu(expanded = aa, onDismissRequest = { aa = false }) { playerController.getAudioTracks().forEach { (i, n) -> DropdownMenuItem(text = { Text(n) }, onClick = { playerController.setAudioTrack(i); aa = false }) }; if (playerController.getAudioTracks().isEmpty()) DropdownMenuItem(text = { Text("Default") }, onClick = { aa = false }) } }
                        Spacer(Modifier.width(6.dp))
                        var ss by remember { mutableStateOf(false) }
                        Box { GreenBtn(Icons.Default.Subtitles) { ss = true }; DropdownMenu(expanded = ss, onDismissRequest = { ss = false }) { DropdownMenuItem(text = { Text("Off") }, onClick = { playerController.setSubtitleTrack(-1); ss = false }); playerController.getSubtitleTracks().forEach { (i, n) -> DropdownMenuItem(text = { Text(n) }, onClick = { playerController.setSubtitleTrack(i); ss = false }) } } }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(if (inBarMode) "◄► Mover cursor  |  OK Abrir  |  ▲ Volver" else "◄► Adelantar  |  ▼ Abrir barra  |  ← Salir", fontSize = 9.sp, color = Color(0x55FFFFFF))
                }
            }
            if (playerController.isLive) { Text("▲▼ Canal  |  ← Volver", fontSize = 10.sp, color = Color(0x55FFFFFF), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)) }
        }
    }
}

@Composable
private fun GreenBtn(icon: ImageVector, extraModifier: Modifier = Modifier, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    IconButton(
        onClick = onClick,
        modifier = extraModifier
            .size(if (isFocused) 54.dp else 40.dp)
            .background(if (isFocused) FocusGreen else Color(0x22FFFFFF), CircleShape)
            .then(if (isFocused) Modifier.border(3.dp, FocusGlow, CircleShape) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .scale(if (isFocused) 1.2f else 1f)
    ) {
        Icon(icon, null, tint = if (isFocused) Color.Black else Color.White, modifier = Modifier.size(if (isFocused) 26.dp else 18.dp))
    }
}
