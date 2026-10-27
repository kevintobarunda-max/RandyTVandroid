package com.randytv.mobile.player

import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@RunWith(AndroidJUnit4::class)
class SoftwareVideoTest {
    @Test fun bundledVp9AndAv1RenderOnSurfaceAndSeekWithAudio() {
        assertTrue("VP9 JNI must load", VideoDecoders.vp9)
        assertTrue("AV1 JNI must load", VideoDecoders.av1)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for ((codec, extension) in listOf("vp9" to "webm", "av1" to "mkv")) {
            val media = File(instrumentation.targetContext.cacheDir, "video-$codec.$extension")
            instrumentation.context.assets.open(media.name).use { input -> media.outputStream().use { input.copyTo(it) } }
            val intent = Intent(instrumentation.targetContext, SeekTestActivity::class.java)
                .putExtra("software_video", codec == "av1")
            ActivityScenario.launch<SeekTestActivity>(intent).use { scenario ->
                fun await(message: String, predicate: (PlayerController) -> Boolean) {
                    val deadline = SystemClock.elapsedRealtime() + 20000
                    while (SystemClock.elapsedRealtime() < deadline) {
                        var ready = false
                        scenario.onActivity {
                            assertNull("$codec: ${it.controller.activePlayer.playerError}", it.controller.activePlayer.playerError)
                            ready = predicate(it.controller)
                        }
                        if (ready) return
                        SystemClock.sleep(50)
                    }
                    fail(message)
                }
                scenario.onActivity {
                    it.controller.activePlayer.apply {
                        setMediaItem(MediaItem.fromUri(media.toURI().toString()))
                        prepare(); play()
                    }
                }
                await("$codec must start") { it.activePlayer.currentPosition > 500 }
                if (codec == "vp9") {
                    // Exercise rebuilding the player/surface, the same recovery used on decoder failure.
                    scenario.onActivity { it.controller.useSoftwareVideo() }
                }
                await("$codec must render actual frames and decoded audio through the bundled renderer") {
                    it.isSoftwareVideo && it.activePlayer.currentPosition > 1000 &&
                        (it.activePlayer.videoDecoderCounters?.renderedOutputBufferCount ?: 0) > 8 &&
                        (it.activePlayer.audioDecoderCounters?.renderedOutputBufferCount ?: 0) > 8
                }
                scenario.onActivity { it.controller.activePlayer.seekTo(5000) }
                await("$codec must resume after a seek") {
                    it.activePlayer.currentPosition > 5500 && it.activePlayer.isPlaying
                }
                scenario.onActivity { println("SOFTWARE_VIDEO_OK codec=$codec frames=${it.controller.activePlayer.videoDecoderCounters?.renderedOutputBufferCount}") }
            }
            media.delete()
        }
    }
}
