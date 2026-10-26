package com.randytv.mobile.player

import androidx.media3.common.Timeline
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection

/** Keep the normal memory/loading budget; only shorten a live channel's initial start. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class LiveLoadControl(private val delegate: LoadControl) : LoadControl {
    enum class Kind { LIVE }
    // Java interface defaults are not forwarded by Kotlin's `by` delegation.
    // Forward every current callback explicitly so DefaultLoadControl owns its lifecycle.
    override fun onPrepared(playerId: PlayerId) = delegate.onPrepared(playerId)
    override fun onTracksSelected(playerId: PlayerId, timeline: Timeline, mediaPeriodId: MediaPeriodId,
        renderers: Array<Renderer>, trackGroups: TrackGroupArray, trackSelections: Array<ExoTrackSelection>) =
        delegate.onTracksSelected(playerId, timeline, mediaPeriodId, renderers, trackGroups, trackSelections)
    override fun onStopped(playerId: PlayerId) = delegate.onStopped(playerId)
    override fun onReleased(playerId: PlayerId) = delegate.onReleased(playerId)
    override fun getAllocator() = delegate.allocator
    override fun getBackBufferDurationUs(playerId: PlayerId) = delegate.getBackBufferDurationUs(playerId)
    override fun retainBackBufferFromKeyframe(playerId: PlayerId) = delegate.retainBackBufferFromKeyframe(playerId)
    override fun shouldContinueLoading(parameters: LoadControl.Parameters) = delegate.shouldContinueLoading(parameters)
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

