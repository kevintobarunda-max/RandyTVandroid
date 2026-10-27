package com.randytv.mobile.player

/** Percentage of the startup/rebuffer time goal, never percentage of a whole film or live show. */
object BufferingProgress {
    const val START_MS = 1000L
    const val REBUFFER_MS = 2500L
    fun percent(bufferedMs: Long, targetMs: Long, remainingMs: Long? = null): Int? {
        if (bufferedMs < 0 || targetMs <= 0 || (remainingMs != null && remainingMs <= 0)) return null
        val target = remainingMs?.let { minOf(it, targetMs) } ?: targetMs
        return (bufferedMs.toDouble() / target * 100).toInt().coerceIn(0, 100)
    }
}
