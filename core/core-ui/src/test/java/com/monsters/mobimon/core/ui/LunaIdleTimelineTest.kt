package com.monsters.mobimon.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LunaIdleTimelineTest {
    @Test
    fun paddedHatKeepsTheOriginalBodyScaleAndGround() {
        for (side in listOf(128, 256, 627, 1254)) {
            val (normalOrigin, normalSize) = lunaIdleDestination(side, LunaAppearance.NORMAL)
            val (hatOrigin, hatSize) = lunaIdleDestination(side, LunaAppearance.HAT)
            assertEquals(normalSize.width, hatSize.width)
            assertEquals(normalOrigin.y + normalSize.height, hatOrigin.y + hatSize.height)
            assertTrue(hatOrigin.y < normalOrigin.y)
            assertEquals(
                lunaIdleDestination(side, LunaAppearance.NORMAL),
                lunaIdleDestination(side, LunaAppearance.SUNGLASSES),
            )
        }
    }

    @Test
    fun breathMovesAtDisplayTicksAndHasASeamlessRestPose() {
        assertTrue(LunaIdleTimeline.scaleAt(32_000_000L) > LunaIdleTimeline.scaleAt(16_000_000L))
        assertEquals(1f, LunaIdleTimeline.scaleAt(0L), 0f)
        assertEquals(1f + 8f / 1070f, LunaIdleTimeline.scaleAt(600_000_000L), 0.000001f)
        assertEquals(1f - 7f / 1070f, LunaIdleTimeline.scaleAt(1_700_000_000L), 0.000001f)
        assertEquals(1f, LunaIdleTimeline.scaleAt(LunaIdleTimeline.CYCLE_NANOS - 1), 0.000001f)
        assertEquals(1f, LunaIdleTimeline.scaleAt(LunaIdleTimeline.CYCLE_NANOS), 0f)
        val beforeSeam = LunaIdleTimeline.scaleAt(2_199_000_000L)
        val afterSeam = LunaIdleTimeline.scaleAt(1_000_000L)
        assertEquals(1f - beforeSeam, afterSeam - 1f, 0.000001f)
    }

    @Test
    fun blinkIsBriefAndDoesNotStraddleTheLoopSeam() {
        assertTrue(!LunaIdleTimeline.eyesClosedAt(1_019_999_999L))
        assertTrue(LunaIdleTimeline.eyesClosedAt(1_020_000_000L))
        assertTrue(LunaIdleTimeline.eyesClosedAt(1_139_999_999L))
        assertTrue(!LunaIdleTimeline.eyesClosedAt(1_140_000_000L))
        assertTrue(!LunaIdleTimeline.eyesClosedAt(LunaIdleTimeline.CYCLE_NANOS))
    }
}
