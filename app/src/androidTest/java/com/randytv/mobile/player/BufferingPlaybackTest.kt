package com.randytv.mobile.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@RunWith(AndroidJUnit4::class)
class BufferingPlaybackTest {
    @get:Rule val compose = createAndroidComposeRule<SeekTestActivity>()
    @Test fun circleShowsMeasuredBufferPercentageOnSlowHttpAndDisappearsWhenReady() {
        val server = MockWebServer()
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("seek-test.mp4").use { it.readBytes() }
        server.enqueue(MockResponse().setHeader("Content-Type", "video/mp4")
            .setBody(Buffer().write(bytes)).throttleBody(4096, 200, TimeUnit.MILLISECONDS))
        server.start()
        try {
            compose.runOnUiThread {
                compose.activity.controller.activePlayer.apply {
                    setMediaItem(MediaItem.fromUri(server.url("/buffer.mp4").toString()))
                    prepare(); play()
                }
            }
            var sampledPercent: Int? = null
            compose.waitUntil(30000) {
                compose.runOnUiThread {
                    val controller = compose.activity.controller
                    assertNull(controller.activePlayer.playerError)
                    sampledPercent = controller.bufferingPercent?.takeIf { it in 1..99 }
                }
                sampledPercent != null
            }
            compose.onNodeWithTag("playback_buffering").assertIsDisplayed()
            compose.onNode(SemanticsMatcher("percentage text") { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.matches(Regex("[0-9]{1,3}%")) } == true
            }).assertIsDisplayed()
            val measured = sampledPercent!!
            compose.runOnUiThread { compose.activity.controller.activePlayer.pause() }
            assertTrue(measured in 1..99)
            compose.waitUntil(30000) {
                var ready = false
                compose.runOnUiThread { ready = compose.activity.controller.activePlayer.playbackState == Player.STATE_READY }
                ready
            }
            compose.onNodeWithTag("playback_buffering").assertDoesNotExist()
        } finally {
            compose.runOnUiThread { compose.activity.controller.activePlayer.stop(); compose.activity.controller.activePlayer.clearMediaItems() }
            server.shutdown()
        }
    }
}
