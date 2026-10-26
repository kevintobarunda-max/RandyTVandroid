package com.randytv.mobile.player

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/** HY300 speakers require decoded PCM, not HDMI/encoded passthrough advertised by some firmware. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class ProjectorRenderersFactory(context: Context, private val preferSoftwareVideo: Boolean = false) : DefaultRenderersFactory(context) {
    init {
        android.util.Log.i("RandyTVAudio", "Software audio ready=${androidx.media3.decoder.ffmpeg.FfmpegLibrary.isAvailable()}")
        android.util.Log.i("RandyTVVideo", "Software video VP9=${VideoDecoders.vp9} AV1=${VideoDecoders.av1}")
        setEnableDecoderFallback(true)
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)
    }
    override fun buildVideoRenderers(context: Context, extensionRendererMode: Int,
        mediaCodecSelector: androidx.media3.exoplayer.mediacodec.MediaCodecSelector,
        enableDecoderFallback: Boolean, eventHandler: android.os.Handler,
        eventListener: androidx.media3.exoplayer.video.VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long, out: java.util.ArrayList<androidx.media3.exoplayer.Renderer>) {
        val start = out.size
        // Audio prefers FFmpeg, but video keeps the projector's hardware first.
        super.buildVideoRenderers(context, EXTENSION_RENDERER_MODE_OFF, mediaCodecSelector,
            true, eventHandler, eventListener, allowedVideoJoiningTimeMs, out)
        val software = java.util.ArrayList<androidx.media3.exoplayer.Renderer>()
        if (VideoDecoders.vp9) software.add(androidx.media3.decoder.vp9.LibvpxVideoRenderer(
            allowedVideoJoiningTimeMs, eventHandler, eventListener, 50, 2, 4, 4))
        if (VideoDecoders.av1) software.add(androidx.media3.decoder.av1.Libgav1VideoRenderer(
            allowedVideoJoiningTimeMs, eventHandler, eventListener, 50, 2, 4, 4))
        out.addAll(if (preferSoftwareVideo) start else out.size, software)
    }
    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean): AudioSink =
        object : ForwardingAudioSink(DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf(androidx.media3.common.audio.ChannelMixingAudioProcessor().apply {
                for (channels in 1..8) putChannelMixingMatrix(
                    androidx.media3.common.audio.ChannelMixingMatrix(channels, 2, StereoMixing.coefficients(channels)))
            }))
            .setEnableFloatOutput(false)
            .setEnableAudioTrackPlaybackParams(false).build()) {
            override fun supportsFormat(format: Format): Boolean =
                format.sampleMimeType == MimeTypes.AUDIO_RAW && super.supportsFormat(format)
            override fun getFormatSupport(format: Format): Int =
                if (format.sampleMimeType == MimeTypes.AUDIO_RAW) super.getFormatSupport(format)
                else AudioSink.SINK_FORMAT_UNSUPPORTED
        }
}
