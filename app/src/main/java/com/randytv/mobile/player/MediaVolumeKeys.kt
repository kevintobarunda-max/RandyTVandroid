package com.randytv.mobile.player

import android.app.Activity
import android.media.AudioManager
import android.view.KeyEvent

/** Handle media volume before Compose/PlayerView can consume keyboard events. */
object MediaVolumeKeys {
    fun dispatch(activity: Activity, event: KeyEvent): Boolean {
        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
            KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
            KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_MUTE -> AudioManager.ADJUST_TOGGLE_MUTE
            else -> return false
        }
        if (event.action == KeyEvent.ACTION_DOWN &&
            (direction != AudioManager.ADJUST_TOGGLE_MUTE || event.repeatCount == 0)) {
            val audio = activity.getSystemService(Activity.AUDIO_SERVICE) as AudioManager
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        }
        return true
    }
}
