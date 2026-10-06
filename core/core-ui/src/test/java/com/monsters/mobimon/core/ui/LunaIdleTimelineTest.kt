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
    fun breathingAdvancesBetweenSpriteBoundaries() {
        assertEquals(0, LunaIdleTimeline.frameAt(16_000_000L))
        assertEquals(0, LunaIdleTimeline.frameAt(32_000_000L))
        assertTrue(
            LunaIdleTimeline.blendAt(32_000_000L, LunaAppearance.NORMAL) >
                LunaIdleTimeline.blendAt(16_000_000L, LunaAppearance.NORMAL),
        )
    }

    @Test
    fun loopSeamApproachesFirstPoseWithoutAnExtraHold() {
        assertEquals(23, LunaIdleTimeline.frameAt(2_199_999_999L))
        assertEquals(1f, LunaIdleTimeline.blendAt(2_199_999_999L, LunaAppearance.NORMAL), 0.00001f)
        assertEquals(0, LunaIdleTimeline.frameAt(LunaIdleTimeline.CYCLE_NANOS))
        assertEquals(0f, LunaIdleTimeline.blendAt(LunaIdleTimeline.CYCLE_NANOS, LunaAppearance.NORMAL), 0f)
    }

    @Test
    fun blinkUsesCrispPosesWhileSunglassesKeepBreathing() {
        for (ms in 800L until 1_200L step 16) {
            assertEquals(0f, LunaIdleTimeline.blendAt(ms * 1_000_000L, LunaAppearance.NORMAL), 0f)
            assertEquals(0f, LunaIdleTimeline.blendAt(ms * 1_000_000L, LunaAppearance.HAT), 0f)
        }
        assertEquals(11, LunaIdleTimeline.frameAt(1_020_000_000L))
        assertEquals(12, LunaIdleTimeline.frameAt(1_080_000_000L))
        assertTrue(LunaIdleTimeline.blendAt(1_030_000_000L, LunaAppearance.SUNGLASSES) > 0f)
    }
}
