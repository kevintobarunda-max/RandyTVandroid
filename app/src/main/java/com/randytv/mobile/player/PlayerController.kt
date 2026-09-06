package com.randytv.mobile.player

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import com.randytv.mobile.data.model.LiveStream
import com.randytv.mobile.data.network.ApiConfig
import kotlinx.coroutines.*

class PlayerController(context: Context) {

    private val audioAttrs = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build()

    // INICIO RAPIDO pero buffer ajustado para POCA RAM (proyector 2GB).
    // Antes: maxBuffer 120s (podia reservar mucha memoria con dos players a la vez).
    // Ahora: 2s para empezar, hasta 30s de buffer maximo -> arranque rapido y bajo consumo.
    private val fastBuffer = DefaultLoadControl.Builder()
        .setBufferDurationsMs(2000, 30000, 500, 2000)
        .build()

    // NextRenderersFactory habilita los decodificadores FFmpeg por software incluidos en NextLib.
    // Con EXTENSION_RENDERER_MODE_PREFER, cuando un codec de audio no lo soporta el hardware del
    // proyector (AC3/E-AC3/DTS/MP2/TrueHD...), ExoPlayer usa el decodificador de software y el
    // audio SI se escucha. Para video se mantiene el hardware (mas eficiente en equipos de 2GB).
    private val renderersFactory = NextRenderersFactory(context)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        .setEnableDecoderFallback(true)

    val player1: ExoPlayer = ExoPlayer.Builder(context, renderersFactory).setLoadControl(fastBuffer).build().apply {
        playWhenReady = true; setAudioAttributes(audioAttrs, true)
        addListener(audioFailureListener { player1AudioUnavailable = it })
    }
    val player2: ExoPlayer = ExoPlayer.Builder(context, renderersFactory).setLoadControl(fastBuffer).build().apply {
        playWhenReady = true; setAudioAttributes(audioAttrs, true)
        addListener(audioFailureListener { player2AudioUnavailable = it })
    }

    var showPlayer1 by mutableStateOf(true)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isLive by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var timeStr by mutableStateOf("00:00")
        private set
    var durationStr by mutableStateOf("--:--")
        private set
    var title by mutableStateOf("")
        private set
    var logo by mutableStateOf("")
        private set
    var currentIdx by mutableIntStateOf(0)
    var aspectMode by mutableIntStateOf(0)
        private set
    var isSeeking by mutableStateOf(false)
        private set
    var isTuning by mutableStateOf(false)
        private set
    var tuningProgress by mutableFloatStateOf(0f)
        private set

    // Indica si el contenido actualmente activo no tiene audio reproducible
    // (codec no compatible con ningun decodificador disponible).
    private var player1AudioUnavailable by mutableStateOf(false)
    private var player2AudioUnavailable by mutableStateOf(false)
    val audioUnavailable: Boolean
        get() = if (showPlayer1) player1AudioUnavailable else player2AudioUnavailable

    var channels: List<LiveStream> = emptyList()
    private var nextReady = false
    private var nextIdx = -1
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    val aspectModes = listOf("Auto", "16:9", "4:3", "Fill")

    val activePlayer: ExoPlayer get() = if (showPlayer1) player1 else player2
    val standbyPlayer: ExoPlayer get() = if (showPlayer1) player2 else player1

    // Detecta si, tras cambiar de pista, el grupo de audio existe pero no quedo
    // seleccionado (el decodificador no pudo reproducirlo), o si hay un error de
    // decodificacion. Reporta el resultado via el callback onResult.
    private fun audioFailureListener(onResult: (Boolean) -> Unit) = object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) {
            val audioGroup = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO }
            val hasAudio = audioGroup != null
            val audioPlayable = audioGroup?.isSelected == true
            onResult(hasAudio && !audioPlayable)
        }
        override fun onPlayerError(error: PlaybackException) {
            if (error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
            ) {
                onResult(true)
            }
        }
    }

    private fun forcePlay(player: ExoPlayer) {
        player.playWhenReady = true; player.play(); player.volume = 1f
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false).build()
    }

    fun playLive(streamId: String, name: String, icon: String) {
        isLive = true; title = name; logo = icon; isPlaying = true; showPlayer1 = true
        isTuning = true; tuningProgress = 0f
        player1AudioUnavailable = false; player2AudioUnavailable = false
        player2.stop(); player2.volume = 0f
        player1.setMediaItem(MediaItem.fromUri(ApiConfig.liveUrl(streamId)))
        player1.prepare(); forcePlay(player1)
        scope.launch {
            var w = 0; while (player1.playbackState != Player.STATE_READY && w < 10000) { delay(50); w += 50; tuningProgress = (w / 10000f).coerceAtMost(0.95f) }
            tuningProgress = 1f; delay(100); isTuning = false
        }
        // Precargar siguiente INMEDIATO
        scope.launch { delay(30); preloadNext() }
    }

    private fun preloadNext() {
        if (channels.isEmpty() || !isLive) return
        val nIdx = (currentIdx + 1) % channels.size
        val ch = channels[nIdx]
        standbyPlayer.stop()
        standbyPlayer.setMediaItem(MediaItem.fromUri(ApiConfig.liveUrl(ch.streamId.toString())))
        standbyPlayer.prepare(); standbyPlayer.playWhenReady = true; standbyPlayer.play(); standbyPlayer.volume = 0f
        // Esperar a que este listo
        scope.launch {
            var w = 0; while (standbyPlayer.playbackState != Player.STATE_READY && w < 30000) { delay(100); w += 100 }
            if (standbyPlayer.playbackState == Player.STATE_READY) { nextReady = true; nextIdx = nIdx; Log.d("RandyTV", "PRELOAD LISTO: ${ch.name}") }
        }
    }

    fun zapNext() {
        if (channels.isEmpty()) return
        currentIdx = (currentIdx + 1) % channels.size
        val ch = channels[currentIdx]

        if (nextReady && nextIdx == currentIdx) {
            // CAMBIO INSTANTANEO
            forcePlay(standbyPlayer)
            activePlayer.volume = 0f; activePlayer.stop()
            title = ch.name; logo = ch.streamIcon
            showPlayer1 = !showPlayer1; nextReady = false; nextIdx = -1
            isTuning = false; tuningProgress = 1f
            scope.launch { delay(30); preloadNext() }
        } else {
            // No precargado - cargar rapido en standby
            isTuning = true; tuningProgress = 0f
            standbyPlayer.stop()
            standbyPlayer.setMediaItem(MediaItem.fromUri(ApiConfig.liveUrl(ch.streamId.toString())))
            standbyPlayer.prepare(); standbyPlayer.playWhenReady = true; standbyPlayer.play(); standbyPlayer.volume = 0f
            scope.launch {
                var w = 0; while (standbyPlayer.playbackState != Player.STATE_READY && w < 10000) { delay(50); w += 50; tuningProgress = (w / 10000f).coerceAtMost(0.95f) }
                tuningProgress = 1f
                forcePlay(standbyPlayer); activePlayer.volume = 0f; activePlayer.stop()
                title = ch.name; logo = ch.streamIcon
                showPlayer1 = !showPlayer1; nextReady = false; nextIdx = -1
                delay(30); isTuning = false; preloadNext()
            }
        }
    }

    fun zapPrev() {
        if (channels.isEmpty()) return
        currentIdx = (currentIdx - 1 + channels.size) % channels.size
        val ch = channels[currentIdx]
        nextReady = false; nextIdx = -1
        isTuning = true; tuningProgress = 0f
        standbyPlayer.stop()
        standbyPlayer.setMediaItem(MediaItem.fromUri(ApiConfig.liveUrl(ch.streamId.toString())))
        standbyPlayer.prepare(); standbyPlayer.playWhenReady = true; standbyPlayer.play(); standbyPlayer.volume = 0f
        scope.launch {
            var w = 0; while (standbyPlayer.playbackState != Player.STATE_READY && w < 10000) { delay(50); w += 50; tuningProgress = (w / 10000f).coerceAtMost(0.95f) }
            tuningProgress = 1f
            forcePlay(standbyPlayer); activePlayer.volume = 0f; activePlayer.stop()
            title = ch.name; logo = ch.streamIcon
            showPlayer1 = !showPlayer1
            delay(30); isTuning = false; preloadNext()
        }
    }

    fun playVOD(streamId: String, ext: String, name: String) {
        stopAll(); isLive = false; title = name; isPlaying = true; showPlayer1 = true
        player1AudioUnavailable = false
        player1.setMediaItem(MediaItem.fromUri(ApiConfig.vodUrl(streamId, ext))); player1.prepare(); forcePlay(player1)
    }

    fun playSeries(episodeId: String, ext: String, name: String) {
        stopAll(); isLive = false; title = name; isPlaying = true; showPlayer1 = true
        player1AudioUnavailable = false
        player1.setMediaItem(MediaItem.fromUri(ApiConfig.seriesUrl(episodeId, ext))); player1.prepare(); forcePlay(player1)
    }

    fun togglePause() { if (activePlayer.isPlaying) activePlayer.pause() else { activePlayer.playWhenReady = true; activePlayer.play() } }
    fun seek(fraction: Float) { isSeeking = true; val d = activePlayer.duration; if (d > 0) { activePlayer.seekTo((fraction * d).toLong()); progress = fraction }; scope.launch { delay(600); isSeeking = false } }
    fun jumpForward(seconds: Int = 30) { activePlayer.seekTo((activePlayer.currentPosition + seconds * 1000L).coerceAtMost(activePlayer.duration)) }
    fun jumpBackward(seconds: Int = 15) { activePlayer.seekTo((activePlayer.currentPosition - seconds * 1000L).coerceAtLeast(0)) }
    fun cycleAspect() { aspectMode = (aspectMode + 1) % aspectModes.size }

    fun getAudioTracks(): List<Pair<Int, String>> {
        val t = mutableListOf<Pair<Int, String>>(); var i = 0
        for (g in activePlayer.currentTracks.groups) { if (g.type == C.TRACK_TYPE_AUDIO) { for (j in 0 until g.length) { val f = g.getTrackFormat(j)
            val lang = when (f.language?.take(2)?.lowercase()) { "es" -> "Espanol"; "en" -> "Ingles"; "pt" -> "Portugues"; "fr" -> "Frances"; "de" -> "Aleman"; "it" -> "Italiano"; "ja" -> "Japones"; else -> null }
            val label = if (!f.label.isNullOrEmpty() && !f.label!!.contains("/") && !f.label!!.contains("http")) f.label else lang ?: "Audio ${i+1}"
            t.add(Pair(i, label!!)); i++ } } }; return t }

    fun getSubtitleTracks(): List<Pair<Int, String>> {
        val t = mutableListOf<Pair<Int, String>>(); var i = 0
        for (g in activePlayer.currentTracks.groups) { if (g.type == C.TRACK_TYPE_TEXT) { for (j in 0 until g.length) { val f = g.getTrackFormat(j)
            val lang = when (f.language?.take(2)?.lowercase()) { "es" -> "Espanol"; "en" -> "Ingles"; "pt" -> "Portugues"; "fr" -> "Frances"; else -> null }
            val label = if (!f.label.isNullOrEmpty() && !f.label!!.contains("/") && !f.label!!.contains("http")) f.label else lang ?: "Sub ${i+1}"
            t.add(Pair(i, label!!)); i++ } } }; return t }

    fun setAudioTrack(index: Int) { var ai = 0; for (g in activePlayer.currentTracks.groups) { if (g.type == C.TRACK_TYPE_AUDIO) { for (j in 0 until g.length) { if (ai == index) { activePlayer.trackSelectionParameters = activePlayer.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, j)).build(); return }; ai++ } } } }
    fun setSubtitleTrack(index: Int) { if (index < 0) { activePlayer.trackSelectionParameters = activePlayer.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build(); return }; activePlayer.trackSelectionParameters = activePlayer.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build(); var si = 0; for (g in activePlayer.currentTracks.groups) { if (g.type == C.TRACK_TYPE_TEXT) { for (j in 0 until g.length) { if (si == index) { activePlayer.trackSelectionParameters = activePlayer.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, j)).build(); return }; si++ } } } }

    fun updateProgress() { if (isSeeking) return; val d = activePlayer.duration; val p = activePlayer.currentPosition; if (d > 0) { progress = p.toFloat() / d.toFloat(); timeStr = fmt(p); durationStr = fmt(d) } }
    private fun fmt(ms: Long): String { val s = (ms / 1000).toInt(); return String.format("%02d:%02d", s / 60, s % 60) }

    fun stopAll() { player1.stop(); player2.stop(); player1.clearMediaItems(); player2.clearMediaItems(); nextReady = false; nextIdx = -1; isTuning = false }
    fun stop() { stopAll(); isPlaying = false; isLive = false; title = ""; logo = ""; progress = 0f; timeStr = "00:00"; durationStr = "--:--" }
    fun release() { scope.cancel(); player1.release(); player2.release() }
}
