package com.randytv.mobile.player

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.media.AudioManager
import android.view.KeyEvent
import com.randytv.mobile.ui.screens.PlayerScreen
import android.os.Bundle
import android.annotation.SuppressLint
import androidx.media3.ui.PlayerView

/** Debug-only host for exercising the actual decoder with a display surface. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class SeekTestActivity : ComponentActivity() {
    lateinit var controller: PlayerController
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = PlayerController(this)
        controller.applyPictureMode(PictureMode.ORIGINAL)
        volumeControlStream = AudioManager.STREAM_MUSIC
        setContent { PlayerScreen(controller, onClose = {}) }
    }
    // Public Activity key hook; AndroidX Core marks its inherited implementation restricted.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        MediaVolumeKeys.dispatch(this, event) || super.dispatchKeyEvent(event)

    override fun onDestroy() {
        controller.release()
        super.onDestroy()
    }
}
