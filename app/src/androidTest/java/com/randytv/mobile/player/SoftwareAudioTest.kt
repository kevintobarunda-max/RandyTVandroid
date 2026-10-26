package com.randytv.mobile.player

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@RunWith(AndroidJUnit4::class)
class SoftwareAudioTest {
    @Test fun softwareDecoderProducesPcmForMovieAndLiveAudioFormats() {
        assertTrue("Bundled native decoder must load", FfmpegLibrary.isAvailable())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val codecs = listOf("ac3", "eac3", "dca", "truehd", "mp3", "flac", "opus", "vorbis", "alac", "ac3-surround")
        ActivityScenario.launch(SeekTestActivity::class.java).use { scenario ->
            for (codec in codecs) {
                val media = File(instrumentation.targetContext.cacheDir, "audio-$codec.mka")
                instrumentation.context.assets.open(media.name).use { input -> media.outputStream().use { input.copyTo(it) } }
                var decoder = ""
                val listener = object : AnalyticsListener {
                    override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime,
                        decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
                        decoder = decoderName
                    }
                }
                scenario.onActivity {
                    it.controller.activePlayer.apply {
                        stop()
                        addAnalyticsListener(listener)
                        setMediaItem(MediaItem.fromUri(media.toURI().toString()))
                        prepare()
                        play()
                    }
                }
                var passed = false
                val deadline = SystemClock.elapsedRealtime() + 15000
                while (!passed && SystemClock.elapsedRealtime() < deadline) {
                    scenario.onActivity {
                        val player = it.controller.activePlayer
                        assertNull("$codec: ${player.playerError}", player.playerError)
                        passed = decoder.startsWith("ffmpeg") && player.currentPosition > 400 &&
                            (player.audioDecoderCounters?.renderedOutputBufferCount ?: 0) > 4
                    }
                    if (!passed) SystemClock.sleep(50)
                }
                assertTrue("$codec must decode through FFmpeg and feed the PCM output; decoder=$decoder", passed)
                println("SOFTWARE_AUDIO_OK codec=$codec decoder=$decoder")
                scenario.onActivity { it.controller.activePlayer.removeAnalyticsListener(listener) }
                media.delete()
            }
        }
    }
}
