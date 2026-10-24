package com.randytv.mobile.player

import android.content.Context
import android.app.ActivityManager
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
import com.randytv.mobile.data.model.LiveStream
import com.randytv.mobile.data.network.ApiConfig
import kotlinx.coroutines.*

class PlayerController(context: Context) {

    private val audioAttrs = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build()

    // A single decoder and bounded sample buffer for 2 GB Android projectors.
    private val lowMemory = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
        .let { manager ->
            val info = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(info)
            manager.isLowRamDevice || info.totalMem <= 3L * 1024 * 1024 * 1024
        }
    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(5000, 15000, 1000, 2500)
        .setTargetBufferBytes(if (lowMemory) 12 * 1024 * 1024 else 24 * 1024 * 1024)
        .setPrioritizeTimeOverSizeThresholds(false)
        .setBackBuffer(0, false)
        .build()

    val activePlayer: ExoPlayer = ExoPlayer.Builder(
        context.applicationContext,
        DefaultRenderersFactory(context.applicationContext).setEnableDecoderFallback(true)
    ).setLoadControl(loadControl).build().apply {
        setAudioAttributes(audioAttrs, true)
        setHandleAudioBecomingNoisy(true)
        if (lowMemory) {
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setMaxVideoSize(1280, 720)
                .setMaxVideoBitrate(2_500_000)
                .setMaxVideoFrameRate(30)
                .build()
        }
        addListener(audioFailureListener { audioUnavailable = it })
        addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                isTuning = isLive && isBuffering
            }
            override fun onIsPlayingChanged(playing: Boolean) { isActuallyPlaying = playing }
            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                isTuning = false
                playbackError = "No se pudo reproducir el contenido. Pulsa Reintentar."
            }
        })
    }

    var isBuffering by mutableStateOf(false)
        private set
    var isActuallyPlaying by mutableStateOf(false)
        private set
    var playbackError by mutableStateOf<String?>(null)
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

    var audioUnavailable by mutableStateOf(false)
        private set
    var channels: List<LiveStream> = emptyList()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    val aspectModes = listOf("Auto", "16:9", "4:3", "Fill")

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

    private fun playUrl(url: String, name: String, icon: String, live: Boolean) {
        scope.coroutineContext.cancelChildren()
        isLive = live
        title = name
        logo = icon
        isPlaying = true
        audioUnavailable = false
        playbackError = null
        isSeeking = false
        progress = 0f
        timeStr = "00:00"
        durationStr = "--:--"
        activePlayer.setMediaItem(MediaItem.fromUri(url))
        activePlayer.prepare()
        forcePlay(activePlayer)
    }

    fun playLive(streamId: String, name: String, icon: String) =
        playUrl(ApiConfig.liveUrl(streamId), name, icon, true)

    fun zapNext() = zap(1)
    fun zapPrev() = zap(-1)

    private fun zap(direction: Int) {
        if (channels.isEmpty() || !isLive) return
        currentIdx = (currentIdx + direction + channels.size) % channels.size
        val channel = channels[currentIdx]
        playLive(channel.streamId.toString(), channel.name, channel.streamIcon)
    }

    fun playVOD(streamId: String, ext: String, name: String) =
        playUrl(ApiConfig.vodUrl(streamId, ext), name, "", false)

    fun playSeries(episodeId: String, ext: String, name: String) =
        playUrl(ApiConfig.seriesUrl(episodeId, ext), name, "", false)

    fun retry() {
        playbackError = null
        activePlayer.prepare()
        activePlayer.play()
    }

    fun togglePause() { if (activePlayer.isPlaying) activePlayer.pause() else { activePlayer.playWhenReady = true; activePlayer.play() } }
    fun seek(fraction: Float) { isSeeking = true; val d = activePlayer.duration; if (d > 0) { activePlayer.seekTo((fraction * d).toLong()); progress = fraction }; scope.launch { delay(600); isSeeking = false } }
    fun jumpForward(seconds: Int = 30) { val duration = activePlayer.duration; if (duration > 0) activePlayer.seekTo((activePlayer.currentPosition + seconds * 1000L).coerceAtMost(duration)) }
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

    fun stopAll() {
        scope.coroutineContext.cancelChildren()
        activePlayer.stop()
        activePlayer.clearMediaItems()
        isTuning = false
        isBuffering = false
        isSeeking = false
        playbackError = null
    }
    fun stop() { stopAll(); isPlaying = false; isLive = false; title = ""; logo = ""; progress = 0f; timeStr = "00:00"; durationStr = "--:--" }
    fun release() { scope.cancel(); activePlayer.release() }
}
