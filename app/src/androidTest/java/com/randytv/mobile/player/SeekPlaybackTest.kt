package com.randytv.mobile.player

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@RunWith(AndroidJUnit4::class)
class SeekPlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync { action() }
    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15000
        while (SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            main { ready = condition() }
            if (ready) return
            SystemClock.sleep(50)
        }
        fail(description)
    }

    @Test fun burstProducesOneSeekAndPreservesPausedState() {
        val context = instrumentation.targetContext
        val video = File(context.cacheDir, "seek-test.mp4")
        instrumentation.context.assets.open("seek-test.mp4").use { input ->
            video.outputStream().use { input.copyTo(it) }
        }
        lateinit var controller: PlayerController
        val scenario = ActivityScenario.launch(SeekTestActivity::class.java)
        scenario.onActivity { controller = it.controller }
        val seekCount = AtomicInteger()
        try {
            main {
                controller.activePlayer.addListener(object : Player.Listener {
                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int
                    ) { if (reason == Player.DISCONTINUITY_REASON_SEEK) seekCount.incrementAndGet() }
                })
                controller.activePlayer.setMediaItem(MediaItem.fromUri(video.toURI().toString()))
                controller.activePlayer.playWhenReady = false
                controller.activePlayer.prepare()
            }
            await("Local VOD should be ready and seekable") {
                controller.canSeek && controller.activePlayer.playbackState == Player.STATE_READY
            }
            main {
                seekCount.set(0)
                repeat(10) { controller.jumpForward(3) }
                assertTrue(controller.progress > 0.30f)
                assertEquals(0, seekCount.get())
            }
            await("A burst should land around 30 seconds") {
                controller.activePlayer.currentPosition in 29000L..31000L &&
                    controller.activePlayer.playbackState == Player.STATE_READY
            }
            main {
                assertEquals(1, seekCount.get())
                assertFalse(controller.activePlayer.playWhenReady)
                repeat(10) { controller.jumpBackward(5) }
                controller.commitSeek()
            }
            await("Backward burst should clamp to the start") {
                controller.activePlayer.currentPosition <= 1000L &&
                    controller.activePlayer.playbackState == Player.STATE_READY
            }
            main {
                controller.seek(1f)
                controller.commitSeek()
            }
            await("Seeking to the end should leave the player before EOF") {
                val player = controller.activePlayer
                player.currentPosition >= player.duration - 5000 &&
                    player.currentPosition < player.duration &&
                    player.playbackState == Player.STATE_READY
            }
            main { assertFalse(controller.activePlayer.playWhenReady) }
        } catch (failure: Throwable) {
            main {
                println("seek diagnostics: state=${controller.activePlayer.playbackState}, " +
                    "position=${controller.activePlayer.currentPosition}, duration=${controller.activePlayer.duration}, " +
                    "seekable=${controller.canSeek}, error=${controller.activePlayer.playerError?.errorCodeName}, " +
                    "playWhenReady=${controller.activePlayer.playWhenReady}")
            }
            throw failure
        } finally {
            scenario.close()
            video.delete()
        }
    }
}
