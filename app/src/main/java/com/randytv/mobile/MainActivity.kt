package com.randytv.mobile

import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randytv.mobile.data.model.LiveStream
import com.randytv.mobile.data.model.SeriesItem
import com.randytv.mobile.data.model.VodStream
import com.randytv.mobile.data.repository.DataRepository
import com.randytv.mobile.player.PlayerController
import com.randytv.mobile.ui.screens.*
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var playerController: PlayerController? = null
    private val repository = DataRepository()
    private var errorMsg: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try { window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } catch (_: Exception) {}
        // Hace que los botones de volumen del control remoto ajusten el volumen MULTIMEDIA
        // (música/video) en toda la app, y no el volumen de llamada u otro stream.
        try { volumeControlStream = android.media.AudioManager.STREAM_MUSIC } catch (_: Exception) {}
        try { playerController = PlayerController(this) } catch (e: Exception) { errorMsg = e.message ?: "Error"; Log.e("RandyTV", "ERROR: $errorMsg", e) }
        try { repository.loadAll() } catch (_: Exception) {}
        setContent {
            val pc = playerController
            if (pc != null) { RandyTVApp(pc, repository) }
            else { Box(modifier = Modifier.fillMaxSize().background(Color.Red), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("ERROR", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.height(8.dp)); Text(errorMsg, color = Color.White, fontSize = 14.sp) } } }
        }
    }

    // Se intercepta aqui (a nivel de Activity) y NO dentro de un Modifier.onKeyEvent de Compose
    // porque el reproductor de video usa un SurfaceView (AndroidView) para pintar el video; ese
    // SurfaceView puede quedarse con el foco de teclado nativo mientras se reproduce, y en ese caso
    // los eventos de teclado NUNCA llegan al onKeyEvent de Compose (por eso el boton de volumen
    // "no respondia" durante la reproduccion). dispatchKeyEvent se ejecuta ANTES que cualquier
    // vista/foco interno, así que aqui el volumen SIEMPRE responde, se este reproduciendo o no.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    (getSystemService(AUDIO_SERVICE) as? AudioManager)?.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI
                    )
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    (getSystemService(AUDIO_SERVICE) as? AudioManager)?.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI
                    )
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_MUTE -> {
                    val muteAdjust = if (android.os.Build.VERSION.SDK_INT >= 23) AudioManager.ADJUST_TOGGLE_MUTE else AudioManager.ADJUST_LOWER
                    (getSystemService(AUDIO_SERVICE) as? AudioManager)?.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC, muteAdjust, AudioManager.FLAG_SHOW_UI
                    )
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() { super.onDestroy(); try { playerController?.release() } catch (_: Exception) {}; try { repository.cancel() } catch (_: Exception) {} }
    override fun onPause() { super.onPause(); try { playerController?.let { if (it.isPlaying) it.activePlayer.pause() } } catch (_: Exception) {} }
    override fun onResume() { super.onResume(); try { playerController?.let { if (it.isPlaying) it.activePlayer.play() } } catch (_: Exception) {} }
}

@Composable
fun RandyTVApp(playerController: PlayerController, repository: DataRepository) {
    var currentScreen by remember { mutableStateOf("splash") }
    var showSimpsons by remember { mutableStateOf(false) }
    var selectedVod by remember { mutableStateOf<VodStream?>(null) }
    var selectedSeries by remember { mutableStateOf<SeriesItem?>(null) }
    var previousScreen by remember { mutableStateOf("home") }

    androidx.activity.compose.BackHandler(enabled = currentScreen != "home" && currentScreen != "splash") {
        when (currentScreen) {
            "player" -> { playerController.stop(); currentScreen = previousScreen }
            "vodDetail" -> currentScreen = "vod"
            "seriesDetail" -> currentScreen = "series"
            else -> { currentScreen = "home"; showSimpsons = false }
        }
    }

    LaunchedEffect(repository.loadDone) { if (repository.loadDone) currentScreen = "home" }
    LaunchedEffect(Unit) { delay(8000); if (currentScreen == "splash") currentScreen = "home" }

    when (currentScreen) {
        "splash" -> SplashScreen(progress = repository.loadProgress)
        "home" -> HomeScreen(
            onNavigateLive = { showSimpsons = false; currentScreen = "live" },
            onNavigateVod = { currentScreen = "vod" },
            onNavigateSeries = { currentScreen = "series" },
            onNavigateSimpsons = { showSimpsons = true; currentScreen = "live" }
        )
        "live" -> ListScreen(type = ListType.LIVE, showSimpsons = showSimpsons, repository = repository,
            onHome = { currentScreen = "home"; showSimpsons = false },
            onTapLive = { stream ->
                // Determinar lista para zapping: si es canal 2MB, usar lista 2MB
                val channelList = if (is2MBChannel(stream)) {
                    get2MBList(repository.liveStreams)
                } else if (showSimpsons) {
                    repository.simpsonsChannels
                } else {
                    repository.liveStreams.filter { it.categoryId == stream.categoryId }
                }

                playerController.channels = channelList
                val idx = channelList.indexOfFirst { it.streamId == stream.streamId }
                if (idx >= 0) playerController.currentIdx = idx

                // REPRODUCIR AUTOMATICAMENTE al seleccionar
                playerController.playLive(stream.streamId.toString(), stream.name, stream.streamIcon)
                previousScreen = "live"
                currentScreen = "player"
            }, onTapVod = {}, onTapSeries = {})
        "vod" -> ListScreen(type = ListType.VOD, repository = repository,
            onHome = { currentScreen = "home" }, onTapLive = {},
            onTapVod = { stream -> selectedVod = stream; previousScreen = "vod"; currentScreen = "vodDetail" }, onTapSeries = {})
        "series" -> ListScreen(type = ListType.SERIES, repository = repository,
            onHome = { currentScreen = "home" }, onTapLive = {}, onTapVod = {},
            onTapSeries = { series -> selectedSeries = series; previousScreen = "series"; currentScreen = "seriesDetail" })
        "vodDetail" -> selectedVod?.let { vod -> VodDetailScreen(stream = vod, onBack = { currentScreen = "vod" },
            onPlay = { playerController.playVOD(it.streamId.toString(), it.containerExtension, it.name); previousScreen = "vodDetail"; currentScreen = "player" }) }
        "seriesDetail" -> selectedSeries?.let { series -> SeriesDetailScreen(series = series, repository = repository, onBack = { currentScreen = "series" },
            onPlayEpisode = { ep -> playerController.playSeries(ep.id, ep.containerExtension, ep.title.ifEmpty { "Ep ${ep.episodeNum}" }); previousScreen = "seriesDetail"; currentScreen = "player" }) }
        "player" -> PlayerScreen(playerController = playerController, onClose = { playerController.stop(); currentScreen = previousScreen })
    }
}
