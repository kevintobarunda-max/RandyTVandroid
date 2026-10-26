package com.randytv.mobile.player

import org.junit.Assert.*
import org.junit.Test

class StereoMixingTest {
    @Test fun dialogueAndSurroundReachBothSpeakersWithoutClipping() {
        for (channels in 1..8) {
            val matrix = StereoMixing.coefficients(channels)
            for (input in 0 until channels) assertTrue(matrix[input * 2] + matrix[input * 2 + 1] > 0f)
            for (output in 0..1) assertTrue((0 until channels).sumOf { matrix[it * 2 + output].toDouble() } <= 1.00001)
            if (channels >= 3) { assertTrue(matrix[4] > 0f); assertTrue(matrix[5] > 0f) }
        }
        assertArrayEquals(floatArrayOf(1f, 0f, 0f, 1f), StereoMixing.coefficients(2), 0f)
    }
}
