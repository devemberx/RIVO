package com.monsters.mobimon.core.ui

import android.content.Context
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
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
class MobiAtlasMemoryTest {
    @Test
    fun overlayRunFitsSixMiBForEveryAppearance() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (accessory in listOf(null, "accessory:mobi_headphones", "accessory:mobi_goggles")) {
            val sheet = requireNotNull(MobiRunSpriteCache.getOrLoad(context, accessory)).asAndroidBitmap()
            assertTrue(
                "124dp run must fit 6 MiB, allocated ${sheet.allocationByteCount}",
                sheet.allocationByteCount <= 6_291_456,
            )
            assertTrue("All 24 frame cells must remain square", sheet.width / 6 == sheet.height / 4)
        }
    }

    @Test
    fun everyOverlayActionPreservesIntegerCellsWithinThePixelBudget() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (accessory in listOf(null, "accessory:mobi_headphones", "accessory:mobi_goggles")) {
            for (action in MobiAtlasAction.entries) {
                val sheet = requireNotNull(MobiAnimationAtlas.load(context, action, accessory, 169)).asAndroidBitmap()
                assertEquals("All cells remain 256px square", 1536, sheet.width)
                assertEquals(1024, sheet.height)
                assertTrue(sheet.allocationByteCount <= 6_291_456)
            }
        }
    }

    @Test
    fun aLargerSlotDoesNotReuseTheSmallCachedAtlas() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val small = requireNotNull(MobiRunSpriteCache.getOrLoad(context, requiredFrameSidePx = 124))
        val large = requireNotNull(MobiRunSpriteCache.getOrLoad(context, requiredFrameSidePx = 372))
        assertNotSame(small, large)
        assertEquals(3072, large.width)
        assertEquals(2048, large.height)
        assertSame(large, MobiRunSpriteCache.getOrLoad(context, requiredFrameSidePx = 372))
        val smallAgain = requireNotNull(MobiRunSpriteCache.getOrLoad(context, requiredFrameSidePx = 124))
        assertEquals(1536, smallAgain.width)
        assertEquals(1024, smallAgain.height)
    }
}
