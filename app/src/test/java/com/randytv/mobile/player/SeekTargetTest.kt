package com.randytv.mobile.player

import org.junit.Assert.*
import org.junit.Test

class SeekTargetTest {
    @Test fun repeatedInputAccumulatesFromPreviewInsteadOfDecoder() {
        val seek = SeekTarget()
        repeat(20) { seek.relative(5000, 10000, 600000) }
        assertEquals(205000L, seek.take())
        assertNull(seek.take())
    }
    @Test fun reversingDirectionUsesCurrentPreview() {
        val seek = SeekTarget()
        seek.relative(30000, 10000, 100000)
        assertEquals(25000L, seek.relative(30000, -15000, 100000))
    }
    @Test fun clampsBeforeStartAndAvoidsEndOfFile() {
        val seek = SeekTarget()
        assertEquals(0L, seek.relative(1000, -30000, 90000))
        assertEquals(88000L, seek.relative(1000, 500000, 90000))
        assertEquals(88000L, seek.fraction(1f, 90000))
    }
    @Test fun unknownDurationAndNonFiniteInputDoNotProduceASeek() {
        val seek = SeekTarget()
        assertNull(seek.relative(0, 10000, -9223372036854775807L))
        assertNull(seek.fraction(Float.NaN, 100000))
        assertNull(seek.fraction(Float.POSITIVE_INFINITY, 100000))
    }
    @Test fun newMediaCanDiscardOldPendingSeek() {
        val seek = SeekTarget()
        seek.relative(0, 30000, 100000)
        seek.clear()
        assertNull(seek.take())
        assertEquals(10000L, seek.relative(0, 10000, 100000))
    }
    @Test fun remoteAccelerationHasPredictableThresholds() {
        assertEquals(10, SeekTarget.remoteStepSeconds(1499))
        assertEquals(30, SeekTarget.remoteStepSeconds(1500))
        assertEquals(60, SeekTarget.remoteStepSeconds(4000))
    }
}
