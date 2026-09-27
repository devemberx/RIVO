package com.monsters.mobimon.core.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MobiWarningAnimationTest {
    @get:Rule val compose = createComposeRule()

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

    @Test
    fun crossfadeTakesTwoHundredMillisecondsAndReversesCurrentOpacity() {
        val warning = mutableStateOf(false)
        val blend = MobiWarningBlend()
        compose.mainClock.autoAdvance = false
        compose.setContent { LaunchedEffect(warning.value) { blend.target(warning.value, true) } }

        fun change(value: Boolean) {
            compose.runOnIdle {
                warning.value = value
                Snapshot.sendApplyNotifications()
            }
            compose.mainClock.advanceTimeByFrame()
        }
        change(true)
        compose.mainClock.advanceTimeBy(100)
        val halfway = blend.opacity.value
        assertTrue(halfway in 0.35f..0.65f)
        change(false)
        assertEquals(halfway, blend.opacity.value, 0.1f)
        compose.mainClock.advanceTimeBy(240)
        assertEquals(0f, blend.opacity.value)
        change(true)
        compose.mainClock.advanceTimeBy(240)
        assertEquals(1f, blend.opacity.value)
        compose.mainClock.advanceTimeBy(9000)
        assertEquals(1f, blend.opacity.value)
        change(false)
        compose.mainClock.advanceTimeBy(240)
        assertEquals(0f, blend.opacity.value)
    }

    @Test
    fun reducedMotionSelectsEndpointsWithoutFading() {
        val warning = mutableStateOf(true)
        val blend = MobiWarningBlend()
        compose.setContent { LaunchedEffect(warning.value) { blend.target(warning.value, false) } }
        compose.waitForIdle()
        assertEquals(1f, blend.opacity.value)
        compose.runOnIdle { warning.value = false }
        compose.waitForIdle()
        assertEquals(0f, blend.opacity.value)
    }

    @Test
    fun hungryCrossfadeAnimatesOpacityOverTwoHundredMilliseconds() {
        val hungry = mutableStateOf(false)
        val blend = MobiHungryBlend()
        compose.mainClock.autoAdvance = false
        compose.setContent { LaunchedEffect(hungry.value) { blend.target(hungry.value, true) } }

        compose.runOnIdle {
            hungry.value = true
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(100)
        val halfway = blend.opacity.value
        assertTrue(halfway in 0.35f..0.65f)
        compose.mainClock.advanceTimeBy(240)
        assertEquals(1f, blend.opacity.value)

        compose.runOnIdle {
            hungry.value = false
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(240)
        assertEquals(0f, blend.opacity.value)
    }
}
