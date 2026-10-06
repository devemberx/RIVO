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
        for (time in listOf(
            1_180_000_000L,
            1_360_000_000L,
            LunaHungryTimeline.STILL_NANOS,
            3_400_000_000L,
            3_600_000_000L,
        )) {
            val result = glasses.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(result)
            canvas.scale(.5f, .5f)
            LunaHungryRenderer(parts).draw(canvas, time, LunaAppearance.SUNGLASSES)
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
                parts.drop,
                parts.cloud,
                parts.dot,
            ).sumOf { it.allocationByteCount } < 300_000,
        )
        for (appearance in LunaAppearance.entries) {
            val artwork = requireNotNull(LunaIdleArtworkCache.getOrLoad(context, appearance))
            val idle = LunaIdleRenderer(artwork)
            var first: Bitmap? = null
            for (ms in listOf(
                0L,
                180L,
                420L,
                680L,
                960L,
                1060L,
                1180L,
                1360L,
                1660L,
                2000L,
                2380L,
                2600L,
                2900L,
                3080L,
                3400L,
                3600L,
                3800L,
                4400L,
            )) {
                val bitmap = Bitmap.createBitmap(760, 840, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.scale(.5f, .5f)
                canvas.translate(0f, 380f)
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
                                "Only face, saliva and thought may change: $appearance ($x,$y)",
                                (x in 246..369 && y in 450..535) ||
                                    (x in 312..335 && y in 500..555) ||
                                    (x in 321..335 && y in 510..735) ||
                                    (x in 464..686 && y in 35..246),
                            )
                            changes++
                        }
                    }
                }
                if (ms in listOf(0L, 3800L, 4400L)) assertEquals(0, changes) else assertTrue(changes > 0)
                if (ms == 3800L) {
                    for (y in 457..525) {
                        for (x in 246..369) {
                            assertEquals(
                                "Closed mouth restores canonical idle pixels",
                                before.getPixel(x, y),
                                bitmap.getPixel(x, y),
                            )
                        }
                    }
                }
                before.recycle()
                val output = File("build/reports/luna-hungry-sequence/${appearance.assetName}-$ms.png")
                output.parentFile?.mkdirs()
                output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                if (ms == 0L) first = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                if (ms == 4400L) assertTrue("The complete layered pose closes its loop", bitmap.sameAs(first))
                bitmap.recycle()
            }
            first?.recycle()
        }
    }

    @Test
    fun thoughtBuildsBeforeTheDripAndFadesAfterIt() {
        for (index in 0..2) {
            val time = (280L + 230L * index) * 1_000_000L
            assertEquals(1f, LunaHungryTimeline.thoughtAlphaAt(time, index), 0f)
            assertEquals(0f, LunaHungryTimeline.thoughtAlphaAt(time, index + 1), 0f)
        }
        assertEquals(1f, LunaHungryTimeline.thoughtAlphaAt(960_000_000L, 3), 0f)
        assertEquals(0f, LunaHungryTimeline.dropAlphaAt(960_000_000L), 0f)
        assertEquals(0f, LunaHungryTimeline.mouthScaleAt(1_060_000_000L), 0f)
        assertEquals(.5f, LunaHungryTimeline.mouthScaleAt(1_360_000_000L), .001f)
        assertEquals(1f, LunaHungryTimeline.mouthScaleAt(1_660_000_000L), 0f)
        assertEquals(0f, LunaHungryTimeline.dropAlphaAt(1_660_000_000L), 0f)
        assertEquals(0f, LunaHungryTimeline.dropAlphaAt(3_080_000_000L), 0f)
        assertEquals(1f, LunaHungryTimeline.thoughtAlphaAt(3_080_000_000L, 3), 0f)
        assertEquals(0f, LunaHungryTimeline.thoughtAlphaAt(3_530_000_000L, 3), 0f)
    }

    @Test
    fun dropletKeepsPositionAndVelocityWhenItDetaches() {
        val release = 2_380_000_000L
        val before = LunaHungryTimeline.dropYAt(release - 1_000_000L)
        val at = LunaHungryTimeline.dropYAt(release)
        val after = LunaHungryTimeline.dropYAt(release + 1_000_000L)
        assertEquals(690f, at, .001f)
        assertEquals(before, after, .002f)
        assertEquals(at - before, after - at, .002f)
        assertEquals(1f, LunaHungryTimeline.dropAlphaAt(release - 1), 0f)
        assertEquals(1f, LunaHungryTimeline.dropAlphaAt(release + 1), 0f)
        val firstFall = LunaHungryTimeline.dropYAt(2_580_000_000L) - at
        val secondFall = LunaHungryTimeline.dropYAt(2_780_000_000L) - LunaHungryTimeline.dropYAt(2_580_000_000L)
        assertTrue("Gravity accelerates the same drop", secondFall > firstFall)
    }

    @Test
    fun closedMouthRestAndStillHungryPoseAreDistinct() {
        for (time in listOf(0L, 3_800_000_000L, LunaHungryTimeline.CYCLE_NANOS - 1, LunaHungryTimeline.CYCLE_NANOS)) {
            assertEquals(0f, LunaHungryTimeline.faceAlphaAt(time), 0f)
            assertEquals(0f, LunaHungryTimeline.dropAlphaAt(time), 0f)
        }
        assertEquals(1f, LunaHungryTimeline.mouthScaleAt(LunaHungryTimeline.STILL_NANOS), 0f)
        assertEquals(1f, LunaHungryTimeline.thoughtAlphaAt(LunaHungryTimeline.STILL_NANOS, 3), 0f)
    }
}
