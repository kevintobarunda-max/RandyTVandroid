package com.randytv.mobile.player

import androidx.media3.common.Timeline
import androidx.media3.exoplayer.LoadControl

/** Keep the normal memory/loading budget; only shorten a live channel's initial start. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class LiveLoadControl(private val delegate: LoadControl) : LoadControl by delegate {
    enum class Kind { LIVE }
    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean {
        val timeline = parameters.timeline
        val periodIndex = timeline.getIndexOfPeriod(parameters.mediaPeriodId.periodUid)
        val live = if (periodIndex >= 0) {
            val period = timeline.getPeriod(periodIndex, Timeline.Period())
            timeline.getWindow(period.windowIndex, Timeline.Window()).mediaItem.localConfiguration?.tag == Kind.LIVE
        } else false
        return LiveStartPolicy.canStart(live, parameters.rebuffering,
            parameters.bufferedDurationUs, parameters.playbackSpeed) || delegate.shouldStartPlayback(parameters)
    }
}

object LiveStartPolicy {
    const val START_MS = 250L
    fun canStart(live: Boolean, rebuffering: Boolean, bufferedUs: Long, speed: Float): Boolean =
        live && !rebuffering && speed > 0 && bufferedUs / speed >= START_MS * 1000
}
