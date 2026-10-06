package com.monsters.mobimon.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MobiIdleAnimationTest {
    @Test
    fun tiltIsSlowSmoothAndIndependentOfBreathing() {
        val period = MobiIdleTimeline.TILT_PERIOD_MS * 1_000_000
        assertEquals(0f, MobiIdleTimeline.tiltAt(0), 0.0001f)
        assertEquals(2.35f, MobiIdleTimeline.tiltAt(period / 4), 0.0001f)
        assertEquals(-2.35f, MobiIdleTimeline.tiltAt(period * 3 / 4), 0.0001f)
        assertEquals(0f, MobiIdleTimeline.tiltAt(period), 0.0001f)
        assertTrue(MobiIdleTimeline.TILT_PERIOD_MS != MobiIdleTimeline.cycleMs)
    }

    @Test
    fun breathAndSettleStayControlledAndMoveIndependently() {
        for (millis in 0L..26_400L step 16) {
            val time = millis * 1_000_000
            assertTrue(MobiIdleTimeline.scaleXAt(time) in 1f..1.0171f)
            assertTrue(MobiIdleTimeline.scaleYAt(time) in 0.9959f..1.0241f)
            assertTrue(MobiIdleTimeline.liftFractionAt(time) in -0.01071f..0f)
        }
        assertEquals(1f, MobiIdleTimeline.breathAt(MobiIdleTimeline.cycleMs * 500_000), 0.0001f)
        assertTrue(MobiIdleTimeline.BOB_PERIOD_MS != MobiIdleTimeline.TILT_PERIOD_MS)
    }
}
