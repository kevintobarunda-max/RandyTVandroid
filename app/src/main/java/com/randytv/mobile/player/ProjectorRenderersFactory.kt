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
class ProjectorRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    init {
        android.util.Log.i("RandyTVAudio", "Software audio ready=${androidx.media3.decoder.ffmpeg.FfmpegLibrary.isAvailable()}")
        setEnableDecoderFallback(true)
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)
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
