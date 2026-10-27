package com.randytv.mobile.player

import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.randytv.mobile.data.model.LiveStream
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@RunWith(AndroidJUnit4::class)
class LiveZapTest {
    @Test fun channelSwitchesReusePlayerAndLatestChannelRendersWithAudio() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bytes = instrumentation.context.assets.open("live-test.ts").use { it.readBytes() }
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse()
                .setHeader("Content-Type", "video/mp2t")
                .setChunkedBody(Buffer().write(bytes), 188 * 50)
        }
        server.start(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), 0)
        val intent = Intent(instrumentation.targetContext, SeekTestActivity::class.java)
            .putExtra("test_live_base", "http://127.0.0.1:${server.port}")
        try {
            ActivityScenario.launch<SeekTestActivity>(intent).use { scenario ->
                lateinit var original: ExoPlayer
                var firstFrame = false
                var requestedAt = 0L
                var firstFrameMs = 0L
                scenario.onActivity {
                    original = it.controller.activePlayer
                    original.addListener(object : Player.Listener {
                        override fun onRenderedFirstFrame() {
                            firstFrame = true
                            firstFrameMs = SystemClock.elapsedRealtime() - requestedAt
                        }
                    })
                    it.controller.channels = (1..3).map { id -> LiveStream(streamId = id, name = "Canal $id") }
                    requestedAt = SystemClock.elapsedRealtime()
                    it.controller.playLive("1", "Canal 1", "")
                }
                fun awaitChannel(id: Int) {
                    val deadline = SystemClock.elapsedRealtime() + 20000
                    while (SystemClock.elapsedRealtime() < deadline) {
                        var ready = false
                        scenario.onActivity {
                            val c = it.controller
                            assertSame(original, c.activePlayer)
                            assertNull(c.activePlayer.playerError)
                            ready = firstFrame && c.activePlayer.isPlaying && c.activePlayer.currentPosition > 500 &&
                                c.activePlayer.currentMediaItem?.localConfiguration?.uri?.path == "/$id.ts" &&
                                (c.activePlayer.audioDecoderCounters?.renderedOutputBufferCount ?: 0) > 8
                        }
                        if (ready) {
                            println("LIVE_ZAP_OK channel=$id localFirstFrameMs=$firstFrameMs")
                            return
                        }
                        SystemClock.sleep(50)
                    }
                    fail("Channel $id must render a new frame and decoded audio")
                }
                awaitChannel(1)
                scenario.onActivity {
                    firstFrame = false
                    requestedAt = SystemClock.elapsedRealtime()
                    it.controller.zapNext()
                }
                awaitChannel(2)
                scenario.onActivity {
                    firstFrame = false
                    requestedAt = SystemClock.elapsedRealtime()
                    // Burst: wrap to 1, then reverse to 3. The last request must win.
                    it.controller.zapNext()
                    it.controller.zapNext()
                    it.controller.zapPrev()
                    assertEquals("Canal 3", it.controller.title)
                    assertEquals(2, it.controller.currentIdx)
                }
                awaitChannel(3)
                scenario.onActivity { it.controller.stop() }
            }
        } finally { server.shutdown() }
    }
}
