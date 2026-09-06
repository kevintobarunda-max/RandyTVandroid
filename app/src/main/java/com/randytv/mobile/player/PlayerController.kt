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
    // IMPORTANTE: usamos EXTENSION_RENDERER_MODE_ON (no PREFER). Con ON, ExoPlayer intenta PRIMERO
    // el decodificador de HARDWARE (rapido, poca CPU) para cada pista de audio/video; solo si el
    // hardware no soporta ese codec (AC3/E-AC3/DTS/TrueHD...) recurre al decodificador FFmpeg por
    // software. Con PREFER (usado antes) el software se probaba SIEMPRE primero, incluso para AAC/
    // MP3 que el hardware YA reproduce bien -- eso rompia el audio en varios titulos que antes
    // sonaban, porque el decodificador FFmpeg de AAC no maneja igual todos los perfiles (HE-AAC,
    // multicanal, etc). Con ON, esos titulos vuelven a usar hardware y siguen sonando, y los
    // titulos con codecs no soportados por hardware siguen cayendo al fallback de software.
    private val renderersFactory = NextRenderersFactory(context)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
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

    // --- Control de concurrencia para el zapping (cambio de canal) -----------------------------
    // Antes, cada pulsacion de "siguiente/anterior canal" lanzaba una corrutina que esperaba a que
    // el reproductor en espera estuviera listo. Si el usuario pulsaba varias veces rapido (lo
    // normal al hacer zapping), las corrutinas viejas seguian vivas y "se pisaban" con la nueva:
    // una corrutina vieja podia terminar tarde y pintar el nombre/logo de UN canal distinto al que
    // realmente estaba en pantalla (el bug del "logo que no es"), o pelear por el mismo reproductor
    // en espera y trabarlo. zapToken se incrementa en cada pulsacion; toda corrutina en vuelo debe
    // verificar que su token siga siendo el vigente antes de tocar el estado visible (titulo, logo,
    // showPlayer1, nextReady/nextIdx). Si no coincide, se descarta en silencio: ya quedo obsoleta.
    private var zapToken = 0L
    private var tuneJob: Job? = null
    private var preloadJob: Job? = null
    private var lastDirection = 1 // +1 = siguiente canal, -1 = anterior. Se usa para precargar en la direccion que el usuario esta usando.

    private fun cancelPendingZapWork() {
        tuneJob?.cancel(); tuneJob = null
        preloadJob?.cancel(); preloadJob = null
    }

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
        cancelPendingZapWork()
        val token = ++zapToken
        isLive = true; title = name; logo = icon; isPlaying = true; showPlayer1 = true
        isTuning = true; tuningProgress = 0f
        player1AudioUnavailable = false; player2AudioUnavailable = false
        nextReady = false; nextIdx = -1
        player2.stop(); player2.volume = 0f
        player1.setMediaItem(MediaItem.fromUri(ApiConfig.liveUrl(streamId)))
        player1.prepare(); forcePlay(player1)
        tuneJob = scope.launch {
            var w = 0
            while (player1.playbackState != Player.STATE_READY && w < 10000) {
                delay(50); w += 50
                if (token != zapToken) return@launch
                tuningProgress = (w / 10000f).coerceAtMost(0.95f)
            }
            if (token != zapToken) return@launch
            tuningProgress = 1f; delay(100)
            if (token == zapToken) isTuning = false
        }
        // Precargar el siguiente canal (en la direccion en la que se venia haciendo zapping) casi
        // de inmediato, para que si el usuario sigue cambiando de canal el salto sea instantaneo.
        preloadJob = scope.launch { delay(30); if (token == zapToken) preloadAdjacent(token, lastDirection) }
    }

    // Prepara en el reproductor "en espera" el canal adyacente (siguiente o anterior segun
    // direction) al que esta activo AHORA MISMO (currentIdx). Valida el token antes y despues de
    // esperar para no pisar el estado si el usuario ya cambio de canal mientras tanto.
    private fun preloadAdjacent(token: Long, direction: Int) {
        if (channels.isEmpty() || !isLive || token != zapToken) return
        val nIdx = (currentIdx + direction + channels.size) % channels.size
        val ch = channels[nIdx]
        standbyPlayer.stop()
        standbyPlayer.setMediaItem(MediaItem.fromUri(ApiConfig.liveUrl(ch.streamId.toString())))
        standbyPlayer.prepare(); standbyPlayer.playWhenReady = true; standbyPlayer.play(); standbyPlayer.volume = 0f
        scope.launch {
            var w = 0
            while (standbyPlayer.playbackState != Player.STATE_READY && w < 30000) {
                delay(100); w += 100
                if (token != zapToken) return@launch // el usuario ya siguio cambiando de canal: descartar
            }
            if (token != zapToken) return@launch
            if (standbyPlayer.playbackState == Player.STATE_READY) { nextReady = true; nextIdx = nIdx; Log.d("RandyTV", "PRELOAD LISTO: ${ch.name}") }
        }
    }

    fun zapNext() = zap(direction = 1)
    fun zapPrev() = zap(direction = -1)

    // Cambia al canal siguiente/anterior. Usa zapToken para invalidar de forma segura cualquier
    // trabajo pendiente de pulsaciones anteriores: si el usuario pulsa varias veces rapido, solo la
    // ULTIMA pulsacion puede terminar actualizando el video/estado visible; las anteriores se
    // abandonan en cuanto detectan que su token quedo obsoleto. Esto evita el bug de "se ve el logo
    // de un canal que no es" (una corrutina vieja pintando datos tarde) y el freeze al zapear rapido.
    private fun zap(direction: Int) {
        if (channels.isEmpty()) return
        cancelPendingZapWork()
        val token = ++zapToken
        lastDirection = direction
        currentIdx = (currentIdx + direction + channels.size) % channels.size
        val ch = channels[currentIdx]

        // Feedback INMEDIATO: nombre y logo cambian al instante al pulsar el boton, sin esperar a
        // que el video este listo. Es lo que hace que el zapping SE SIENTA instantaneo aunque el
        // video tarde unos milisegundos en aparecer.
        title = ch.name; logo = ch.streamIcon
        player1AudioUnavailable = false; player2AudioUnavailable = false

        if (nextReady && nextIdx == currentIdx) {
            // Ya estaba precargado exactamente este canal -> swap real instantaneo.
            forcePlay(standbyPlayer)
            activePlayer.volume = 0f; activePlayer.stop()
            showPlayer1 = !showPlayer1; nextReady = false; nextIdx = -1
            isTuning = false; tuningProgress = 1f
            preloadJob = scope.launch { delay(30); if (token == zapToken) preloadAdjacent(token, direction) }
        } else {
            // No estaba precargado (cambio de direccion, salto de varios canales, o el usuario
            // zapeo mas rapido de lo que tardo la precarga anterior). Se prepara en el reproductor
            // en espera SIN detener el que esta en pantalla, para no dejar la imagen congelada
            // mientras carga; solo se hace el swap cuando el nuevo canal esta REALMENTE listo.
            isTuning = true; tuningProgress = 0f
            nextReady = false; nextIdx = -1
            standbyPlayer.stop()
            standbyPlayer.setMediaItem(MediaItem.fromUri(ApiConfig.liveUrl(ch.streamId.toString())))
            standbyPlayer.prepare(); standbyPlayer.playWhenReady = true; standbyPlayer.play(); standbyPlayer.volume = 0f
            tuneJob = scope.launch {
                var w = 0
                while (standbyPlayer.playbackState != Player.STATE_READY && w < 10000) {
                    delay(50); w += 50
                    if (token != zapToken) return@launch // pulsacion obsoleta: abandonar sin tocar la UI ni el estado
                    tuningProgress = (w / 10000f).coerceAtMost(0.95f)
                }
                if (token != zapToken) return@launch
                tuningProgress = 1f
                forcePlay(standbyPlayer); activePlayer.volume = 0f; activePlayer.stop()
                showPlayer1 = !showPlayer1
                delay(30)
                if (token != zapToken) return@launch
                isTuning = false
                preloadAdjacent(token, direction)
            }
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

    fun stopAll() { cancelPendingZapWork(); zapToken++; player1.stop(); player2.stop(); player1.clearMediaItems(); player2.clearMediaItems(); nextReady = false; nextIdx = -1; isTuning = false }
    fun stop() { stopAll(); isPlaying = false; isLive = false; title = ""; logo = ""; progress = 0f; timeStr = "00:00"; durationStr = "--:--" }
    fun release() { scope.cancel(); player1.release(); player2.release() }
}
