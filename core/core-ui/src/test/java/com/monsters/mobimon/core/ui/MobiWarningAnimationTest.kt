package com.monsters.mobimon.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MobiWarningAnimationTest {
    @Test
    fun timelineCyclesThrough24FramesOverFourSeconds() {
        val cycleNanos = MobiCollapsedTimeline.CYCLE_MS * 1_000_000L
        assertEquals(0, MobiCollapsedTimeline.frameAt(0L))
        assertEquals(12, MobiCollapsedTimeline.frameAt(cycleNanos / 2))
        assertEquals(23, MobiCollapsedTimeline.frameAt(cycleNanos - 1_000_000L))
        assertEquals(0, MobiCollapsedTimeline.frameAt(cycleNanos))
    }

    @Test
    fun timelineBlendCalculatesSubFrameProgress() {
        val frameNanos = (MobiCollapsedTimeline.CYCLE_MS * 1_000_000L) / MobiCollapsedTimeline.FRAME_COUNT
        assertEquals(0f, MobiCollapsedTimeline.blendAt(0L), 0.001f)
        assertEquals(0.5f, MobiCollapsedTimeline.blendAt(frameNanos / 2), 0.01f)
        assertEquals(0f, MobiCollapsedTimeline.blendAt(frameNanos), 0.001f)
    }

    @Test
    fun dizzyStarsTimelineCyclesThrough12Frames() {
        val cycleNanos = MobiDizzyStarsTimeline.CYCLE_MS * 1_000_000L
        assertEquals(0, MobiDizzyStarsTimeline.frameAt(0L))
        assertEquals(6, MobiDizzyStarsTimeline.frameAt(cycleNanos / 2))
        assertEquals(11, MobiDizzyStarsTimeline.frameAt(cycleNanos - 1_000_000L))
        assertEquals(0, MobiDizzyStarsTimeline.frameAt(cycleNanos))
    }
}
