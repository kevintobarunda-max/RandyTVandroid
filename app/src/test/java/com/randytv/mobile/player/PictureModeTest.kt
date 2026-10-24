package com.randytv.mobile.player

import org.junit.Assert.*
import org.junit.Test

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class PictureModeTest {
    private fun color(mode: PictureMode, rgb: FloatArray, hdr: Boolean = false): FloatArray {
        val matrix = ProjectorBrightness(mode).getMatrix(0, hdr)
        return FloatArray(3) { row ->
            matrix[row] * rgb[0] + matrix[4 + row] * rgb[1] +
                matrix[8 + row] * rgb[2] + matrix[12 + row]
        }
    }

    @Test fun maximumLiftsDarkAndMidGrayMoreThanOtherPresets() {
        for (input in listOf(0.05f, 0.25f, 0.5f)) {
            val rgb = floatArrayOf(input, input, input)
            val maximum = color(PictureMode.MAXIMUM, rgb)[0]
            for (mode in PictureMode.values().filter { it != PictureMode.MAXIMUM }) {
                assertTrue(mode.name, maximum > color(mode, rgb)[0])
            }
        }
    }

    @Test fun presetsDoNotTintNeutralGray() {
        for (mode in PictureMode.values()) {
            val rgb = color(mode, floatArrayOf(0.3f, 0.3f, 0.3f))
            assertEquals(rgb[0], rgb[1], 0.00001f)
            assertEquals(rgb[1], rgb[2], 0.00001f)
        }
    }

    @Test fun hdrIsLeftUnchangedInEveryPreset() {
        val input = floatArrayOf(0.1f, 0.3f, 0.8f)
        for (mode in PictureMode.values()) {
            assertArrayEquals(input, color(mode, input, hdr = true), 0.00001f)
        }
    }

    @Test fun savedSelectionRestoresAndNewInstallUsesMaximum() {
        assertEquals(PictureMode.MAXIMUM, PictureMode.fromSaved(null))
        assertEquals(PictureMode.MAXIMUM, PictureMode.fromSaved("obsolete"))
        for (mode in PictureMode.values()) assertEquals(mode, PictureMode.fromSaved(mode.name))
        assertTrue(PictureMode.MAXIMUM.maximumWindowBrightness)
        assertFalse(PictureMode.ORIGINAL.maximumWindowBrightness)
    }
}
