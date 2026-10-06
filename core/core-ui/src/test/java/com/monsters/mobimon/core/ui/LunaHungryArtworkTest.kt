package com.monsters.mobimon.core.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LunaHungryArtworkTest {
    @Test
    fun facialCorrectionDoesNotEraseSunglasses() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val parts = requireNotNull(LunaHungryPartsCache.getOrLoad(context))
        val glasses = requireNotNull(LunaIdleArtworkCache.getOrLoad(context, LunaAppearance.SUNGLASSES)).body
        val normal = requireNotNull(LunaIdleArtworkCache.getOrLoad(context)).body
        val result = glasses.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        canvas.scale(.5f, .5f)
        LunaHungryRenderer(parts).draw(canvas, 0L, LunaAppearance.SUNGLASSES)
        for (y in 260 until 325) {
            for (x in 245 until 370) {
                val source = glasses.getPixel(x, y)
                val bare = normal.getPixel(x, y)
                // Strong equipment pixels distinguish the frame/lenses from their antialiased boundary.
                if (android.graphics.Color.red(bare) - android.graphics.Color.red(source) > 50) {
                    assertEquals("The hungry face must not paint over a lens", source, result.getPixel(x, y))
                }
            }
        }
        result.recycle()
    }

    @Test
    fun hungerPreservesIdleCapBodyAndGroundForEveryAppearance() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val parts = requireNotNull(LunaHungryPartsCache.getOrLoad(context))
        val renderer = LunaHungryRenderer(parts)
        assertTrue(
            listOf(
                parts.skin,
                parts.skinGlasses,
                parts.mouth,
                parts.thought,
            ).sumOf { it.allocationByteCount } < 300_000,
        )
        for (appearance in LunaAppearance.entries) {
            val artwork = requireNotNull(LunaIdleArtworkCache.getOrLoad(context, appearance))
            val idle = LunaIdleRenderer(artwork)
            var first: Bitmap? = null
            for (ms in listOf(0L, 550L, 1050L, 1100L, 1650L, 2200L)) {
                val bitmap = Bitmap.createBitmap(760, 760, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.scale(.5f, .5f)
                canvas.translate(0f, 240f)
                val save = canvas.save()
                if (appearance == LunaAppearance.HAT) canvas.translate(0f, -192f)
                idle.draw(canvas, ms * 1_000_000L)
                canvas.restoreToCount(save)
                val before = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                renderer.draw(canvas, ms * 1_000_000L, appearance)
                var changes = 0
                for (y in 0 until bitmap.height) {
                    for (x in 0 until bitmap.width) {
                        if (bitmap.getPixel(x, y) != before.getPixel(x, y)) {
                            assertTrue(
                                "Only face and thought may change: $appearance ($x,$y)",
                                (x in 246..369 && y in 387..459) || (x in 530..665 && y in 41..179),
                            )
                            changes++
                        }
                    }
                }
                assertTrue(changes > 1000)
                before.recycle()
                val output = File("build/reports/luna-hungry-layers/${appearance.assetName}-$ms.png")
                output.parentFile?.mkdirs()
                output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                if (ms == 0L) first = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                if (ms == 2200L) assertTrue("The complete layered pose closes its loop", bitmap.sameAs(first))
                bitmap.recycle()
            }
            first?.recycle()
        }
    }

    @Test
    fun localGestureHasContinuousLoopAndDisplayTickMotion() {
        val duration = LunaHungryTimeline.CYCLE_NANOS
        assertEquals(LunaHungryTimeline.mouthScaleAt(0), LunaHungryTimeline.mouthScaleAt(duration), 0f)
        assertEquals(LunaHungryTimeline.thoughtLiftAt(0), LunaHungryTimeline.thoughtLiftAt(duration), 0f)
        assertEquals(1f, LunaHungryTimeline.mouthScaleAt(duration - 1), .000001f)
        assertEquals(0f, LunaHungryTimeline.thoughtLiftAt(duration - 1), .000001f)
        assertTrue(LunaHungryTimeline.thoughtLiftAt(32_000_000) > LunaHungryTimeline.thoughtLiftAt(16_000_000))
        assertEquals(.68f, LunaHungryTimeline.mouthScaleAt(duration / 2), .000001f)
    }
}
