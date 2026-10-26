package com.randytv.mobile.ui.screens

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import android.view.KeyEvent
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import android.os.SystemClock
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.randytv.mobile.player.PictureMode
import com.randytv.mobile.player.PlayerController
import com.randytv.mobile.ui.components.CachedImage
import com.randytv.mobile.ui.components.FocusGreen
import com.randytv.mobile.ui.components.FocusGlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@Composable
fun PlayerScreen(playerController: PlayerController, onClose: () -> Unit) {
    val context = LocalContext.current
    var showPictureSettings by remember { mutableStateOf(false) }
    val activity = context as? Activity
    DisposableEffect(activity) {
        val originalBrightness = activity?.window?.attributes?.screenBrightness
        onDispose {
            if (activity != null && originalBrightness != null) {
                activity.window.attributes = activity.window.attributes.apply { screenBrightness = originalBrightness }
            }
        }
    }
    LaunchedEffect(activity, playerController.pictureMode) {
        activity?.let {
            it.window.attributes = it.window.attributes.apply {
                screenBrightness = if (playerController.pictureMode.maximumWindowBrightness) 1f else -1f
            }
        }
    }
    var uiVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var hideJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val mainFocus = remember { FocusRequester() }
    val firstBtnFocus = remember { FocusRequester() }
    var heldSince by remember { mutableStateOf(0L) }
    var lastSeekKey by remember { mutableIntStateOf(-1) }
    var lastSeekEvent by remember { mutableStateOf(0L) }
    var sliderFraction by remember { mutableStateOf<Float?>(null) }
    var inBarMode by remember { mutableStateOf(false) }
    LaunchedEffect(uiVisible, playerController.isLive) {
        while (uiVisible && !playerController.isLive) {
            playerController.updateProgress()
            delay(500)
        }
    }
    LaunchedEffect(Unit) { mainFocus.requestFocus() }
    LaunchedEffect(showPictureSettings) {
        if (showPictureSettings) hideJob?.cancel()
    }
    val player = playerController.activePlayer
    LaunchedEffect(player, playerController.isBuffering) {
        while (playerController.isBuffering) {
            playerController.updateBufferingProgress()
            delay(200)
        }
    }
    val nativeView = remember(context, player) {
        PlayerView(context).apply { useController = false; this.player = player }
    }
    DisposableEffect(player, nativeView) {
        onDispose { playerController.cancelPendingSeek(); nativeView.player = null }
    }

    fun showUI() { uiVisible = true; hideJob?.cancel(); hideJob = scope.launch { delay(if (inBarMode) 20000 else 4000); uiVisible = false; inBarMode = false } }
    LaunchedEffect(Unit) { showUI() }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val ratio = when (playerController.aspectMode) { 1 -> 16f / 9f; 2 -> 4f / 3f; else -> null }
            val videoModifier = if (ratio == null) Modifier.fillMaxSize() else {
                val width = minOf(maxWidth, maxHeight * ratio)
                Modifier.size(width, width / ratio)
            }
            AndroidView(factory = { nativeView }, update = {
                it.resizeMode = if (playerController.aspectMode == 0)
                    AspectRatioFrameLayout.RESIZE_MODE_FIT else AspectRatioFrameLayout.RESIZE_MODE_FILL
            }, modifier = videoModifier)
        }

        // Zona principal
        Box(modifier = Modifier.fillMaxSize()
            .then(if (!inBarMode) Modifier.focusRequester(mainFocus).focusable() else Modifier)
            .onKeyEvent { event ->
                if (inBarMode) return@onKeyEvent false
                val code = event.key.nativeKeyCode
                val seekKey = code in listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
                if (event.type == KeyEventType.KeyUp && seekKey) {
                    playerController.commitSeek()
                    lastSeekKey = -1
                    return@onKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (!playerController.isLive && seekKey) {
                    val now = SystemClock.uptimeMillis()
                    if (lastSeekKey != code) { heldSince = now; lastSeekEvent = 0L; lastSeekKey = code }
                    if (now - lastSeekEvent >= 160) {
                        playerController.remoteSeek(code == KeyEvent.KEYCODE_DPAD_RIGHT ||
                            code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, now - heldSince)
                        lastSeekEvent = now
                    }
                    showUI()
                    return@onKeyEvent true
                }
                when (event.key.nativeKeyCode) {
                    KeyEvent.KEYCODE_MENU -> { showPictureSettings = true; true }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { if (playerController.isLive) { playerController.zapNext(); showUI() }; true }
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { if (playerController.isLive) { playerController.zapPrev(); showUI() } else { inBarMode = true; uiVisible = true; hideJob?.cancel(); scope.launch { delay(150); firstBtnFocus.requestFocus() } }; true }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> { if (!playerController.isLive) playerController.jumpBackward(10); showUI(); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { if (!playerController.isLive) playerController.jumpForward(10); showUI(); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { if (!playerController.isLive) playerController.togglePause() else showPictureSettings = true; showUI(); true }
                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { onClose(); true }
                    else -> false
                }
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { showUI() }) }
            .pointerInput(Unit) { detectDragGestures { _, d -> if (playerController.isLive) { if (d.y < -50) { playerController.zapNext(); showUI() } else if (d.y > 50) { playerController.zapPrev(); showUI() } else if (d.x > 80) onClose() } else { if (d.x > 80) onClose() } } }
        )

        playerController.seekMessage?.let { message ->
            Text(message, color = Color.White, fontSize = 22.sp,
                modifier = Modifier.align(Alignment.Center).background(Color(0xCC000000), RoundedCornerShape(10.dp)).padding(18.dp))
        }
        if (playerController.isBuffering && playerController.seekMessage == null) {
            PlaybackBufferingIndicator(playerController.bufferingPercent, Modifier.align(Alignment.Center))
        }
        playerController.playbackError?.let { message ->
            Column(Modifier.align(Alignment.Center).background(Color(0xDD000000)).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(message, color = Color.White)
                Button(onClick = { playerController.retry() }) { Text("Reintentar") }
            }
        }
        if (playerController.audioUnavailable) {
            Text("Audio no compatible con este dispositivo",
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp))
        }

        if (uiVisible) {
            playerController.videoNotice?.let {
                Text(it, color = Color.White, fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp).background(Color(0xBB000000)).padding(6.dp))
            }
            TextButton(
                onClick = { showPictureSettings = true },
                modifier = Modifier.align(Alignment.TopCenter).background(Color(0xBB000000))
            ) { Text("${playerController.pictureMode.label} · Menú", color = Color.White) }
            Row(modifier = Modifier.align(Alignment.TopStart).padding(16.dp).background(Color(0xAA000000), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { if (playerController.logo.isNotEmpty()) { CachedImage(url = playerController.logo, height = 32.dp, modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp))); Spacer(Modifier.width(8.dp)) }; Column { Text(playerController.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp)); if (playerController.isLive) Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(5.dp).background(Color.Red, CircleShape)); Spacer(Modifier.width(4.dp)); Text("EN VIVO", fontSize = 9.sp, color = Color.Red, fontWeight = FontWeight.Bold) } } }
            if (!playerController.isTuning) { Box(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(36.dp).background(Color(0x80000000), CircleShape).clickable { onClose() }, contentAlignment = Alignment.Center) { Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.size(18.dp)) } }

            if (!playerController.isLive) {
                Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().onKeyEvent {
                    if (it.type == KeyEventType.KeyDown && it.key.nativeKeyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        inBarMode = false
                        scope.launch { delay(50); mainFocus.requestFocus() }
                        true
                    } else false
                }.background(Color(0xDD000000)).padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Box(Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(progress = playerController.bufferedProgress,
                            modifier = Modifier.fillMaxWidth().align(Alignment.Center).height(4.dp),
                            color = Color(0x66FFFFFF), trackColor = Color(0x33FFFFFF))
                        Slider(
                            value = sliderFraction ?: playerController.progress.coerceIn(0f, 1f),
                            onValueChange = { sliderFraction = it },
                            onValueChangeFinished = {
                                sliderFraction?.let { playerController.seek(it); playerController.commitSeek() }
                                sliderFraction = null
                                showUI()
                            },
                            enabled = playerController.canSeek,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(thumbColor = FocusGreen, activeTrackColor = FocusGreen,
                                inactiveTrackColor = Color.Transparent)
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(playerController.timeStr, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0x99FFFFFF)); Text(playerController.durationStr, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0x99FFFFFF)) }
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        GreenBtn(Icons.Default.Replay10, Modifier.focusRequester(firstBtnFocus)) { playerController.jumpBackward(10) }
                        Spacer(Modifier.width(6.dp))
                        GreenBtn(if (playerController.isActuallyPlaying) Icons.Default.Pause else Icons.Default.PlayArrow) { playerController.togglePause() }
                        Spacer(Modifier.width(6.dp))
                        GreenBtn(Icons.Default.Forward10) { playerController.jumpForward(10) }
                        Spacer(Modifier.weight(1f))
                        var sa by remember { mutableStateOf(false) }
                        Box { GreenBtn(Icons.Default.AspectRatio) { sa = true }; DropdownMenu(expanded = sa, onDismissRequest = { sa = false }) { playerController.aspectModes.forEachIndexed { i, m -> DropdownMenuItem(text = { Text(m + if (i == playerController.aspectMode) " *" else "") }, onClick = { playerController.selectAspect(i); sa = false }) } } }
                        Spacer(Modifier.width(6.dp))
                        var aa by remember { mutableStateOf(false) }
                        Box { GreenBtn(Icons.Default.VolumeUp) { aa = true }; DropdownMenu(expanded = aa, onDismissRequest = { aa = false }) { playerController.getAudioTracks().forEach { (i, n) -> DropdownMenuItem(text = { Text(n) }, onClick = { playerController.setAudioTrack(i); aa = false }) }; if (playerController.getAudioTracks().isEmpty()) DropdownMenuItem(text = { Text("Default") }, onClick = { aa = false }) } }
                        Spacer(Modifier.width(6.dp))
                        var ss by remember { mutableStateOf(false) }
                        Box { GreenBtn(Icons.Default.Subtitles) { ss = true }; DropdownMenu(expanded = ss, onDismissRequest = { ss = false }) { DropdownMenuItem(text = { Text("Off") }, onClick = { playerController.setSubtitleTrack(-1); ss = false }); playerController.getSubtitleTracks().forEach { (i, n) -> DropdownMenuItem(text = { Text(n) }, onClick = { playerController.setSubtitleTrack(i); ss = false }) } } }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(if (inBarMode) "◄► Mover cursor  |  OK Abrir  |  ▲ Volver" else "◄► ±10 s · Mantener: 30/60 s · Soltar: aplicar | ▼ Controles", fontSize = 9.sp, color = Color(0x55FFFFFF))
                }
            }
            if (playerController.isLive) { Text("▲▼ Canal  |  ← Volver", fontSize = 10.sp, color = Color(0x55FFFFFF), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)) }
        }
    }
    if (showPictureSettings) {
        AlertDialog(
            onDismissRequest = { showPictureSettings = false },
            title = { Text("Imagen · HY300") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Modo activo: ${playerController.pictureMode.label}")
                    PictureMode.values().forEach { mode ->
                        OutlinedButton(
                            onClick = { playerController.applyPictureMode(mode) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text((if (mode == playerController.pictureMode) "✓ " else "") + mode.label)
                                Text(mode.description, fontSize = 12.sp)
                            }
                        }
                    }
                    Text("Máximo brillo fuerza el aclarado digital más alto de la app y solicita brillo Android al 100% durante la reproducción.")
                    Text("No aumenta la potencia del LED. El firmware puede ignorar el brillo Android. Los filtros se aplican a video SDR; HDR conserva su imagen original.")
                    Text("La selección se guarda para TV, películas y series. Cambiar de modo puede causar una pausa breve.")
                    TextButton(onClick = {
                        try { context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
                        catch (_: android.content.ActivityNotFoundException) {
                            try { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                            catch (_: android.content.ActivityNotFoundException) { }
                        }
                    }) { Text("Abrir ajustes de pantalla") }
                    playerController.pictureNotice?.let { Text(it) }
                }
            },
            confirmButton = { TextButton(onClick = { showPictureSettings = false }) { Text("Volver al video") } }
        )
    }

}

@Composable
private fun GreenBtn(icon: ImageVector, extraModifier: Modifier = Modifier, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    IconButton(
        onClick = onClick,
        modifier = extraModifier
            .size(48.dp)
            .background(if (isFocused) FocusGreen else Color(0x22FFFFFF), CircleShape)
            .then(if (isFocused) Modifier.border(3.dp, FocusGlow, CircleShape) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .scale(if (isFocused) 1.2f else 1f)
    ) {
        Icon(icon, null, tint = if (isFocused) Color.Black else Color.White, modifier = Modifier.size(if (isFocused) 26.dp else 18.dp))
    }
}

/** The percentage describes the buffer needed to resume, including a live stream's time buffer. */
@Composable
fun PlaybackBufferingIndicator(percent: Int?, modifier: Modifier = Modifier) {
    Column(modifier.background(Color(0xCC000000), RoundedCornerShape(12.dp)).padding(16.dp)
        .testTag("playback_buffering"), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
            if (percent == null) CircularProgressIndicator(Modifier.fillMaxSize(), color = FocusGlow)
            else {
                CircularProgressIndicator(progress = percent.coerceIn(0, 100) / 100f,
                    modifier = Modifier.fillMaxSize(), color = FocusGlow, trackColor = Color(0x44FFFFFF))
                Text("${percent.coerceIn(0, 100)}%", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(if (percent == 100) "Búfer listo · preparando imagen…" else "Cargando búfer…", color = Color.White, fontSize = 12.sp)
    }
}
