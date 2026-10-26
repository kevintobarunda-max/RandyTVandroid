package com.randytv.mobile.player

import org.junit.Assert.*
import org.junit.Test

class BufferingProgressTest {
    @Test fun measuresStartupRebufferAndLiveTimeGoals() {
        assertEquals(0, BufferingProgress.percent(0, BufferingProgress.START_MS))
        assertEquals(50, BufferingProgress.percent(500, BufferingProgress.START_MS))
        assertEquals(20, BufferingProgress.percent(500, BufferingProgress.REBUFFER_MS))
        assertEquals(100, BufferingProgress.percent(25000, BufferingProgress.REBUFFER_MS))
        assertEquals(50, BufferingProgress.percent(100, 1000, 200))
        assertEquals(100, BufferingProgress.percent(Long.MAX_VALUE, 1000))
        assertNull(BufferingProgress.percent(-1, 1000))
        assertNull(BufferingProgress.percent(100, 0))
        assertNull(BufferingProgress.percent(100, 1000, 0))
    }
}
