package com.randytv.mobile.player

import android.content.Context
import android.os.Build
import androidx.media3.common.MimeTypes
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
import androidx.media3.exoplayer.SeekParameters
import com.randytv.mobile.data.model.LiveStream
import com.randytv.mobile.data.network.ApiConfig
import kotlinx.coroutines.*

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class PlayerController(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences("projector", Context.MODE_PRIVATE)
    var pictureMode by mutableStateOf(PictureMode.fromSaved(preferences.getString("picture_mode", null)))
        private set
    var pictureNotice by mutableStateOf<String?>(null)
        private set

    private val audioAttrs = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build()

    // A single decoder and bounded sample buffer for 2 GB Android projectors.
    private val lowMemory = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
        .let { manager ->
            val info = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(info)
            Build.MODEL.contains("HY300", ignoreCase = true) || manager.isLowRamDevice || info.totalMem <= 3L * 1024 * 1024 * 1024
        }
    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(5000, 15000, 1000, 2500)
        .setTargetBufferBytes(if (lowMemory) 12 * 1024 * 1024 else 24 * 1024 * 1024)
        .setPrioritizeTimeOverSizeThresholds(false)
        .setBackBuffer(8000, true)
        .build()

    val activePlayer: ExoPlayer = ExoPlayer.Builder(
        context.applicationContext,
        DefaultRenderersFactory(context.applicationContext).setEnableDecoderFallback(true)
    ).setLoadControl(loadControl).build().apply {
        setSeekParameters(SeekParameters.CLOSEST_SYNC)
        setAudioAttributes(audioAttrs, true)
        setHandleAudioBecomingNoisy(true)
        if (lowMemory) {
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setPreferredVideoMimeTypes(MimeTypes.VIDEO_H264)
                .setPreferredAudioMimeTypes(MimeTypes.AUDIO_AAC)
                .setMaxVideoSize(1280, 720)
                .setMaxVideoBitrate(2_500_000)
                .setMaxVideoFrameRate(30)
                .build()
        }
        if (pictureMode != PictureMode.ORIGINAL) setVideoEffects(listOf(ProjectorBrightness(pictureMode)))
        addListener(audioFailureListener { audioUnavailable = it })
        addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                isTuning = isLive && isBuffering
                if (playbackState == Player.STATE_READY && seekTarget.pendingMs == null) isSeeking = false
            }
            override fun onEvents(player: Player, events: Player.Events) {
                canSeek = !isLive && player.isCurrentMediaItemSeekable && player.duration > 0
                if (canSeek && seekTarget.pendingMs == null && !isSeeking) seekMessage = null
            }
            override fun onIsPlayingChanged(playing: Boolean) { isActuallyPlaying = playing }
            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                isTuning = false
                if (pictureMode != PictureMode.ORIGINAL && error.errorCode in listOf(
                        PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED,
                        PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED)) {
                    applyPictureMode(PictureMode.ORIGINAL)
                    pictureNotice = "Aclarado desactivado: el procesador gráfico no admite este efecto."
                    return
                }
                playbackError = "No se pudo reproducir el contenido. Pulsa Reintentar."
            }
        })
    }

    private val seekTarget = SeekTarget()
    private var seekJob: Job? = null
    var canSeek by mutableStateOf(false)
        private set
    var seekMessage by mutableStateOf<String?>(null)
        private set
    var bufferedProgress by mutableFloatStateOf(0f)
        private set

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
        cancelPendingSeek()
        scope.coroutineContext.cancelChildren()
        canSeek = false
        bufferedProgress = 0f
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

    /** Reconfigure effects only while stopped, as required by Media3 1.2. */
    fun applyPictureMode(next: PictureMode) {
        if (next == pictureMode) return
        val item = activePlayer.currentMediaItem
        val position = seekTarget.pendingMs ?: activePlayer.currentPosition
        cancelPendingSeek()
        val shouldPlay = activePlayer.playWhenReady
        activePlayer.stop()
        pictureMode = next
        preferences.edit().putString("picture_mode", next.name).apply()
        pictureNotice = null
        activePlayer.setVideoEffects(
            if (next == PictureMode.ORIGINAL) emptyList() else listOf(ProjectorBrightness(next))
        )
        if (item != null) {
            activePlayer.setMediaItem(item, if (isLive) C.TIME_UNSET else position)
            activePlayer.prepare()
            activePlayer.playWhenReady = shouldPlay
        }
    }

    fun retry() {
        playbackError = null
        activePlayer.prepare()
        activePlayer.play()
    }

    fun togglePause() { if (activePlayer.isPlaying) activePlayer.pause() else { activePlayer.playWhenReady = true; activePlayer.play() } }
    private fun queueSeek(targetMs: Long?) {
        if (targetMs == null) return
        isSeeking = true
        seekMessage = "Ir a ${fmt(targetMs)}"
        progress = targetMs.toFloat() / activePlayer.duration
        timeStr = fmt(targetMs)
        seekJob?.cancel()
        seekJob = scope.launch { delay(300); commitSeek() }
    }

    private fun seekSupported(): Boolean {
        if (isLive || activePlayer.duration <= 0 || !activePlayer.isCurrentMediaItemSeekable) {
            seekMessage = "Este contenido todavía no permite adelantar."
            return false
        }
        return true
    }

    fun seek(fraction: Float) {
        if (seekSupported()) queueSeek(seekTarget.fraction(fraction, activePlayer.duration))
    }

    private fun jump(seconds: Int) {
        if (seekSupported()) queueSeek(seekTarget.relative(
            activePlayer.currentPosition, seconds * 1000L, activePlayer.duration))
    }

    fun jumpForward(seconds: Int = 10) = jump(seconds)
    fun jumpBackward(seconds: Int = 10) = jump(-seconds)
    fun remoteSeek(forward: Boolean, heldMs: Long) {
        val seconds = SeekTarget.remoteStepSeconds(heldMs)
        jump(if (forward) seconds else -seconds)
    }

    /** One actual decoder seek for a burst of remote events. Keeps pause/play intent. */
    fun commitSeek() {
        seekJob?.cancel()
        seekJob = null
        val targetMs = seekTarget.take() ?: return
        if (!isLive && activePlayer.isCurrentMediaItemSeekable) {
            activePlayer.setSeekParameters(
                if (targetMs >= activePlayer.duration - 5000) SeekParameters.PREVIOUS_SYNC
                else SeekParameters.CLOSEST_SYNC
            )
            activePlayer.seekTo(targetMs)
            isSeeking = activePlayer.playbackState == Player.STATE_BUFFERING
            seekMessage = null
        } else {
            isSeeking = false
        }
    }

    fun cancelPendingSeek() {
        seekJob?.cancel()
        seekJob = null
        seekTarget.clear()
        isSeeking = false
        seekMessage = null
    }

    fun selectAspect(index: Int) { aspectMode = index.coerceIn(aspectModes.indices) }
    fun cycleAspect() { selectAspect((aspectMode + 1) % aspectModes.size) }

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

    fun updateProgress() {
        val duration = activePlayer.duration
        if (duration <= 0) return
        bufferedProgress = (activePlayer.bufferedPosition.toFloat() / duration).coerceIn(0f, 1f)
        durationStr = fmt(duration)
        if (seekTarget.pendingMs != null) return
        val position = activePlayer.currentPosition
        progress = (position.toFloat() / duration).coerceIn(0f, 1f)
        timeStr = fmt(position)
    }
    private fun fmt(ms: Long): String {
        val seconds = (ms.coerceAtLeast(0) / 1000)
        return if (seconds >= 3600) String.format("%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
            else String.format("%02d:%02d", seconds / 60, seconds % 60)
    }

    fun stopAll() {
        cancelPendingSeek()
        canSeek = false
        bufferedProgress = 0f
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
