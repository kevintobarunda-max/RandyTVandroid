package com.randytv.mobile.player

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@RunWith(AndroidJUnit4::class)
class HlsPlaybackTest {
    @Test fun hlsRedirectsAndRelativeSegmentsRenderVideoWithAudio() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = instrumentation.context.assets
        val files = assets.list("hls")!!.associateWith { assets.open("hls/$it").use { input -> input.readBytes() } }
        val segments = AtomicInteger()
        val redirects = AtomicInteger()
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path!!.substringBefore('?')
                if (path == "/1.m3u8") {
                    redirects.incrementAndGet()
                    return MockResponse().setResponseCode(302).setHeader("Location", "/media/index.m3u8?token=test-only")
                }
                val name = path.substringAfterLast('/')
                val bytes = files[name] ?: return MockResponse().setResponseCode(404)
                if (name.endsWith(".ts")) segments.incrementAndGet()
                return MockResponse().setHeader("Content-Type", if (name.endsWith(".m3u8")) "application/vnd.apple.mpegurl" else "video/mp2t")
                    .setBody(Buffer().write(bytes))
            }
        }
        server.start(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), 0)
        val intent = Intent(instrumentation.targetContext, SeekTestActivity::class.java)
            .putExtra("test_live_base", "http://127.0.0.1:${server.port}")
            .putExtra("test_live_extension", "m3u8")
        try {
            ActivityScenario.launch<SeekTestActivity>(intent).use { scenario ->
                scenario.onActivity { it.controller.playLive("1", "HLS con redirección", "") }
                val deadline = SystemClock.elapsedRealtime() + 20000
                var played = false
                while (SystemClock.elapsedRealtime() < deadline && !played) {
                    scenario.onActivity {
                        val p = it.controller.activePlayer
                        assertNull(p.playerError)
                        played = p.currentPosition > 1000 && p.isPlaying &&
                            (p.videoDecoderCounters?.renderedOutputBufferCount ?: 0) > 8 &&
                            (p.audioDecoderCounters?.renderedOutputBufferCount ?: 0) > 8
                    }
                    if (!played) SystemClock.sleep(50)
                }
                assertTrue("HLS must produce actual video frames and PCM audio", played)
                assertTrue(redirects.get() > 0)
                assertTrue(segments.get() > 0)
                println("HLS_HTTP_OK redirects=${redirects.get()} segments=${segments.get()}")
            }
        } finally { server.shutdown() }
    }
}
