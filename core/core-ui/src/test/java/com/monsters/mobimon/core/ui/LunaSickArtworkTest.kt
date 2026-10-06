package com.monsters.mobimon.core.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LunaSickArtworkTest {
    @Test
    fun sickLayersShareHeatAndPreserveStillPoseAndLoopContact() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        LunaSickArtworkCache.clear()
        val normal = requireNotNull(LunaSickArtworkCache.getOrLoad(context))
        for (appearance in LunaAppearance.entries) {
            val artwork = requireNotNull(LunaSickArtworkCache.getOrLoad(context, appearance))
            assertTrue(artwork.heat === normal.heat)
            assertTrue(artwork === LunaSickArtworkCache.peek(appearance))
            assertTrue(
                artwork.still === LunaFirstFrameCache.getOrLoad(context, LunaActiveAnimation.SICK, appearance),
            )
            val renderer = LunaSickRenderer(artwork, appearance)
            val first = render(renderer, 0)
            assertTrue("Still and animated first pose must agree", first.sameAs(artwork.still.asAndroidBitmap()))
            val last = render(renderer, 2200)
            assertTrue("Loop must return to the identical pose", first.sameAs(last))
            for (ms in listOf(550L, 1100L, 1650L)) {
                val sample = render(renderer, ms)
                assertFalse("Sick motion must update between raster-frame boundaries", first.sameAs(sample))
                assertTrue("Collapsed ground must remain fixed", kotlin.math.abs(bottom(first) - bottom(sample)) <= 1)
                save(sample, appearance, ms)
                sample.recycle()
            }
            save(first, appearance, 0)
            save(last, appearance, 2200)
            first.recycle()
            last.recycle()
        }
    }

    @Test
    fun originalSweatFlowsAcrossForeheadAndCheekWithoutFullBodyFrames() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        for (appearance in LunaAppearance.entries) {
            val artwork = requireNotNull(LunaSickArtworkCache.getOrLoad(context, appearance))
            val renderer = LunaSickRenderer(artwork, appearance)
            val first = render(renderer, 0)
            val middle = render(renderer, 1100)
            var changes = 0
            for (y in 175..412) {
                for (x in 315..557) {
                    if (first.getPixel(x, y) != middle.getPixel(x, y)) changes++
                }
            }
            assertTrue("Flowing sweat changes inside the head at the same breath scale", changes > 100)
            assertTrue("Only local patches are retained", artwork.sweat.width < artwork.body.width * 2)
            assertTrue(
                "Sweat interpolates within a former 90ms raster interval",
                !render(renderer, 16).sameAs(render(renderer, 32)),
            )
            first.recycle()
            middle.recycle()
        }
    }

    private fun render(
        renderer: LunaSickRenderer,
        ms: Long,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(627, 627, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(.5f, .5f)
        renderer.draw(canvas, ms * 1_000_000L)
        return bitmap
    }

    private fun bottom(bitmap: Bitmap): Int {
        for (y in bitmap.height - 1 downTo 0) {
            for (x in 0 until bitmap.width) if (Color.alpha(bitmap.getPixel(x, y)) > 127) return y
        }
        error("Missing sick artwork")
    }

    private fun save(
        bitmap: Bitmap,
        appearance: LunaAppearance,
        ms: Long,
    ) {
        val file = java.io.File("build/reports/luna-sick-layers/${appearance.assetName}-$ms.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
