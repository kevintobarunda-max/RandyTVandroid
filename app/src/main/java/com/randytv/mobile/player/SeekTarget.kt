package com.randytv.mobile.player

/** Accumulates input against the preview, rather than the stale decoder position. */
class SeekTarget {
    var pendingMs: Long? = null
        private set

    fun relative(currentMs: Long, deltaMs: Long, durationMs: Long): Long? {
        if (durationMs <= 0) return null
        val base = pendingMs ?: currentMs
        val limit = (durationMs - 5000).coerceAtLeast(0)
        pendingMs = (base.coerceIn(0, limit) + deltaMs).coerceIn(0, limit)
        return pendingMs
    }

    fun fraction(fraction: Float, durationMs: Long): Long? {
        if (durationMs <= 0 || !fraction.isFinite()) return null
        pendingMs = (fraction.coerceIn(0f, 1f).toDouble() * durationMs).toLong()
            .coerceIn(0, (durationMs - 5000).coerceAtLeast(0))
        return pendingMs
    }

    fun take(): Long? = pendingMs.also { pendingMs = null }
    fun clear() { pendingMs = null }

    companion object {
        fun remoteStepSeconds(heldMs: Long): Int = when {
            heldMs >= 4000 -> 60
            heldMs >= 1500 -> 30
            else -> 10
        }
    }
}
