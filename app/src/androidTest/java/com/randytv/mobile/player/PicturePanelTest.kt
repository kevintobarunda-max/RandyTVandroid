package com.randytv.mobile.player

import android.graphics.Bitmap
import android.graphics.Color
import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.MediaItem
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@RunWith(AndroidJUnit4::class)
class PicturePanelTest {
    @get:Rule val compose = createAndroidComposeRule<SeekTestActivity>()
    @Test fun sidePanelKeepsVideoVisibleAndShowsActualBrightnessChange() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixture = File(instrumentation.targetContext.cacheDir, "picture-test.mp4")
        instrumentation.context.assets.open(fixture.name).use { input -> fixture.outputStream().use { input.copyTo(it) } }
        val proof = instrumentation.targetContext.getExternalFilesDir("picture-proof")!!
        proof.mkdirs()
        compose.runOnUiThread {
            compose.activity.controller.activePlayer.apply {
                setMediaItem(MediaItem.fromUri(fixture.toURI().toString()))
                prepare(); play()
            }
        }
        fun playing(): Boolean {
            var result = false
            compose.runOnUiThread {
                val c = compose.activity.controller
                assertNull(c.activePlayer.playerError)
                result = c.activePlayer.isPlaying && !c.isBuffering && c.activePlayer.currentPosition > 300
            }
            return result
        }
        compose.waitUntil(20000) { playing() }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MENU)
        compose.onNodeWithTag("picture_side_panel").assertIsDisplayed()
        // Popup must leave most of the screen for the video; it does not dim that area.
        val panel = compose.onNodeWithTag("picture_side_panel").fetchSemanticsNode().boundsInRoot
        fun capture(name: String): Bitmap = instrumentation.uiAutomation.takeScreenshot()!!.also { bitmap ->
            File(proof, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        fun luminance(bitmap: Bitmap): Int {
            val x = (bitmap.width * .2f).toInt()
            val y = bitmap.height / 2
            var sum = 0
            for (dx in -4..4) for (dy in -4..4) {
                val rgb = bitmap.getPixel(x + dx, y + dy)
                sum += (Color.red(rgb) + Color.green(rgb) + Color.blue(rgb)) / 3
            }
            return sum / 81
        }
        compose.waitUntil(10000) { playing() }
        val before = capture("original.png")
        assertTrue(panel.width < before.width * .5f)
        val originalLevel = luminance(before)
        assertTrue("The preview must contain the gray video frame", originalLevel in 25..110)
        before.recycle()
        compose.onNodeWithTag("picture_mode_MAXIMUM").performScrollTo().performClick()
        var brightLevel = 0
        compose.waitUntil(20000) {
            if (!playing()) false else {
                val frame = instrumentation.uiAutomation.takeScreenshot()!!
                brightLevel = luminance(frame)
                frame.recycle()
                brightLevel > originalLevel + 15
            }
        }
        compose.onNodeWithTag("picture_side_panel").assertIsDisplayed()
        compose.onNodeWithTag("active_picture_mode").assertTextContains("Máximo brillo", substring = true)
        capture("maximum.png").recycle()
        compose.runOnUiThread { assertEquals(PictureMode.MAXIMUM, compose.activity.controller.pictureMode) }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("picture_side_panel").assertDoesNotExist()
        compose.waitUntil(10000) { playing() }
        println("PICTURE_PANEL_OK original=$originalLevel maximum=$brightLevel")
        compose.runOnUiThread { compose.activity.controller.applyPictureMode(PictureMode.ORIGINAL) }
    }
}
