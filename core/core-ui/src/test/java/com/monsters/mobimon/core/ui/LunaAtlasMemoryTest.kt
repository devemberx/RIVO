package com.monsters.mobimon.core.ui

import android.content.Context
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "mdpi")
class LunaAtlasMemoryTest {
    @Test
    fun overlaySceneScaleSelectsSmallCellsWithoutChangingRegistration() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(239, LunaScene.requiredFrameSidePx(124f, LunaAppearTimeline.SCENE_SCALE))
        assertEquals(256, LunaScene.requiredFrameSidePx(124f, LunaDisappearTimeline.SCENE_SCALE))
        assertEquals(513, LunaScene.requiredFrameSidePx(248f, LunaDisappearTimeline.SCENE_SCALE))
        for (appearance in LunaAppearance.entries) {
            assertEquals(1536, LunaAppearTimeline.load(context, appearance)?.width)
            assertEquals(1536, LunaDisappearTimeline.load(context, appearance)?.width)
        }
    }

    @Test
    fun overlayRunFitsSixMiBForEveryAppearance() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (appearance in LunaAppearance.entries) {
            val sheet = requireNotNull(LunaRunAnimationCache.getOrLoad(context, appearance))
            val allocation = sheet.asAndroidBitmap().allocationByteCount.toLong()
            assertTrue("$appearance run allocated $allocation bytes", allocation <= 6_291_456)
        }
    }

    @Test
    fun allActionsUseIntegerCellsAtBothDecodeTiers() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (appearance in LunaAppearance.entries) {
            for (action in LunaAtlasAction.entries) {
                for (target in listOf(256, 257, 512)) {
                    val sheet = requireNotNull(LunaAnimationAtlas.load(context, action, appearance, target))
                    val cell = if (target <= 256) 256 else 512
                    assertEquals(cell * 6, sheet.width)
                    assertEquals(cell * 4, sheet.height)
                    assertEquals(cell * cell * 24 * 4, sheet.asAndroidBitmap().allocationByteCount)
                    sheet.asAndroidBitmap().recycle()
                }
            }
        }
    }

    @Test
    fun cacheRejectsDifferentEquipmentAndResolution() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        LunaRunAnimationCache.clear()
        val small = requireNotNull(LunaRunAnimationCache.getOrLoad(context, LunaAppearance.HAT, 124))
        assertSame(small, LunaRunAnimationCache.getOrLoad(context, LunaAppearance.HAT, 256))
        assertNull(LunaRunAnimationCache.peek(LunaAppearance.NORMAL, 124))
        assertNull(LunaRunAnimationCache.peek(LunaAppearance.HAT, 372))
        val large = requireNotNull(LunaRunAnimationCache.getOrLoad(context, LunaAppearance.HAT, 372))
        assertNotSame(small, large)
        assertEquals(3072, large.width)
        val glasses = requireNotNull(LunaRunAnimationCache.getOrLoad(context, LunaAppearance.SUNGLASSES, 124))
        assertNotSame(large, glasses)
        assertSame(glasses, LunaRunAnimationCache.peek(LunaAppearance.SUNGLASSES, 124))
        LunaRunAnimationCache.clear()
        assertNull(LunaRunAnimationCache.peek(LunaAppearance.SUNGLASSES, 124))
    }
}
