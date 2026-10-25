package com.randytv.mobile.player

import android.app.Activity
import android.os.Bundle
import androidx.media3.ui.PlayerView

/** Debug-only host for exercising the actual decoder with a display surface. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class SeekTestActivity : Activity() {
    lateinit var controller: PlayerController
    private lateinit var video: PlayerView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = PlayerController(this)
        controller.applyPictureMode(PictureMode.ORIGINAL)
        video = PlayerView(this).apply {
            useController = false
            player = controller.activePlayer
        }
        setContentView(video)
    }
    override fun onDestroy() {
        video.player = null
        controller.release()
        super.onDestroy()
    }
}
