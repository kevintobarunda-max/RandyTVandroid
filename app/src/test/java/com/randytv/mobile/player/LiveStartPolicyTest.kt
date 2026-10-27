package com.randytv.mobile.player

import org.junit.Assert.*
import org.junit.Test

class LiveStartPolicyTest {
    @Test fun liveStartsWithQuarterSecondButKeepsRecoveryAndVodThresholds() {
        assertFalse(LiveStartPolicy.canStart(true, false, 249999, 1f))
        assertTrue(LiveStartPolicy.canStart(true, false, 250000, 1f))
        assertFalse(LiveStartPolicy.canStart(false, false, 250000, 1f))
        assertFalse(LiveStartPolicy.canStart(true, true, 250000, 1f))
        assertFalse(LiveStartPolicy.canStart(true, false, 250000, 2f))
        assertTrue(LiveStartPolicy.canStart(true, false, 500000, 2f))
        assertFalse(LiveStartPolicy.canStart(true, false, 0, 1f))
    }
}
