package com.randytv.mobile.player

import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.media3.common.C
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
class AudioPlaybackTest {
    @Test fun audibleTrackDecodesSwitchesAndHardwareVolumeWorksWithPlayerFocused() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val audio = context.getSystemService(AudioManager::class.java)
        val original = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val wasMuted = audio.isStreamMute(AudioManager.STREAM_MUSIC)
        val video = File(context.cacheDir, "audio-test.mp4")
        instrumentation.context.assets.open("seek-test.mp4").use { input -> video.outputStream().use { input.copyTo(it) } }
        val scenario = ActivityScenario.launch(SeekTestActivity::class.java)
        lateinit var controller: PlayerController
        fun await(message: String, predicate: () -> Boolean) {
            val end = SystemClock.elapsedRealtime() + 15000
            while (SystemClock.elapsedRealtime() < end) {
                var ready = false
                scenario.onActivity { ready = predicate() }
                if (ready) return
                SystemClock.sleep(50)
            }
            fail(message)
        }
        try {
            scenario.onActivity {
                controller = it.controller
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, 3, 0)
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                controller.activePlayer.setMediaItem(MediaItem.fromUri(video.toURI().toString()))
                controller.activePlayer.prepare()
                controller.activePlayer.play()
            }
            await("AAC audio buffers must reach output while playback advances") {
                (controller.activePlayer.audioDecoderCounters?.renderedOutputBufferCount ?: 0) > 10 &&
                    controller.activePlayer.currentPosition > 500 && !controller.audioUnavailable
            }
            scenario.onActivity {
                assertTrue(controller.getAudioTracks().size >= 2)
                controller.activePlayer.trackSelectionParameters = controller.activePlayer.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
                controller.setAudioTrack(1)
            }
            await("Selecting the other language must re-enable audio and select the second track") {
                controller.activePlayer.audioFormat?.language == "en" || controller.activePlayer.audioFormat?.language == "eng"
            }
            scenario.onActivity { activity ->
                assertFalse(controller.activePlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO))
                val before = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                assertTrue(activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP)))
                assertTrue(activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP)))
                assertEquals(before + 1, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN))
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN))
                assertEquals(before, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE))
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_MUTE))
                assertTrue(audio.isStreamMute(AudioManager.STREAM_MUSIC))
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE))
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_MUTE))
                assertFalse(audio.isStreamMute(AudioManager.STREAM_MUSIC))
                assertEquals(1f, controller.activePlayer.volume)
            }
        } finally {
            scenario.close()
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, original, 0)
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                if (wasMuted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, 0)
            video.delete()
        }
    }
}
