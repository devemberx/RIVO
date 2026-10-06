package com.monsters.mobimon.core.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
    fun exposedTorsoRemainsContinuousBehindTheNearHand() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        LunaSickArtworkCache.clear()
        for (appearance in LunaAppearance.entries) {
            val body = requireNotNull(LunaSickArtworkCache.getOrLoad(context, appearance)).body.asAndroidBitmap()
            // This exposed flank was erased when the foreground paw was reduced.
            for (y in 364 until 369) {
                for (x in 190 until 197) {
                    assertTrue(
                        "$appearance must retain solid torso skin behind the hand at ($x, $y)",
                        Color.alpha(body.getPixel(x, y)) >= 250,
                    )
                }
            }
        }
    }

    @Test
    fun exposedFlankShadingHasNoOpaqueMaskSeam() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        LunaSickArtworkCache.clear()
        for (appearance in LunaAppearance.entries) {
            val body = requireNotNull(LunaSickArtworkCache.getOrLoad(context, appearance)).body.asAndroidBitmap()
            // This continuous skin region previously contained a straight white mask edge.
            for (y in 416 until 429) {
                for (x in 167 until 179) {
                    val left = body.getPixel(x, y)
                    val right = body.getPixel(x + 1, y)
                    val jump =
                        maxOf(
                            kotlin.math.abs(Color.red(left) - Color.red(right)),
                            kotlin.math.abs(Color.green(left) - Color.green(right)),
                            kotlin.math.abs(Color.blue(left) - Color.blue(right)),
                        )
                    assertTrue("$appearance flank shading must remain continuous at ($x, $y)", jump <= 12)
                }
            }
        }
    }

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
            val last = render(renderer, 4400)
            assertTrue("Loop must return to the identical pose", first.sameAs(last))
            for (ms in listOf(550L, 1100L, 2200L, 3300L)) {
                val sample = render(renderer, ms)
                assertFalse("Sick motion must update between raster-frame boundaries", first.sameAs(sample))
                assertTrue("Collapsed ground must remain fixed", kotlin.math.abs(bottom(first) - bottom(sample)) <= 1)
                save(sample, appearance, ms)
                sample.recycle()
            }
            save(first, appearance, 0)
            save(last, appearance, 4400)
            first.recycle()
            last.recycle()
        }
    }

    @Test
    fun originalSweatFlowsAcrossForeheadAndCheekWithoutFullBodyFrames() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        for (appearance in LunaAppearance.entries) {
            val artwork = requireNotNull(LunaSickArtworkCache.getOrLoad(context, appearance))
            val empty = Bitmap.createBitmap(627, 627, Bitmap.Config.ARGB_8888)
            val frozen = Bitmap.createBitmap(732, 1062, Bitmap.Config.ARGB_8888)
            val atlasCanvas = Canvas(frozen)
            for (index in 0 until 24) {
                val x = index % 4 * 183
                val y = index / 4 * 177
                atlasCanvas.drawBitmap(
                    artwork.sweat.asAndroidBitmap(),
                    Rect(0, 0, 183, 177),
                    Rect(
                        x,
                        y,
                        x + 183,
                        y + 177,
                    ),
                    null,
                )
            }
            val sweatOnly = artwork.copy(body = empty.asImageBitmap(), heat = empty.asImageBitmap())
            val renderer = LunaSickRenderer(sweatOnly, appearance)
            val heldRenderer = LunaSickRenderer(sweatOnly.copy(sweat = frozen.asImageBitmap()), appearance)
            val first = render(renderer, 0)
            val heldFirst = render(heldRenderer, 0)
            assertTrue("Both atlases start with the same authored patches", first.sameAs(heldFirst))
            val middle = render(renderer, 1100)
            val heldMiddle = render(heldRenderer, 1100)
            var changes = 0
            for (y in 280..650) {
                for (x in 430..850) {
                    if (heldMiddle.getPixel(x, y) != middle.getPixel(x, y)) changes++
                }
            }
            assertTrue("Authored sweat must flow independently of identical body transforms", changes > 100)
            assertTrue("Only local patches are retained", artwork.sweat.width < artwork.body.width * 2)
            for (ms in listOf(16L, 32L)) {
                val actual = render(renderer, ms)
                val held = render(heldRenderer, ms)
                assertFalse("Sweat interpolates inside a former 90ms interval", actual.sameAs(held))
                actual.recycle()
                held.recycle()
            }
            first.recycle()
            heldFirst.recycle()
            middle.recycle()
            heldMiddle.recycle()
            frozen.recycle()
            empty.recycle()
        }
    }

    @Test
    fun bodyGestureMovesIndependentlyOfStatusEffects() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val artwork = requireNotNull(LunaSickArtworkCache.getOrLoad(context))
        val empty = Bitmap.createBitmap(artwork.heat.width, artwork.heat.height, Bitmap.Config.ARGB_8888)
        val emptySweat = Bitmap.createBitmap(artwork.sweat.width, artwork.sweat.height, Bitmap.Config.ARGB_8888)
        val renderer =
            LunaSickRenderer(
                artwork.copy(heat = empty.asImageBitmap(), sweat = emptySweat.asImageBitmap()),
                LunaAppearance.NORMAL,
            )
        val rest = render(renderer, 0)
        for (ms in listOf(1100L, 2200L, 3300L)) {
            val pose = render(renderer, ms)
            assertFalse("Body must move independently of status effects", rest.sameAs(pose))
            assertTrue("Ground remains planted", kotlin.math.abs(bottom(rest) - bottom(pose)) <= 1)
            pose.recycle()
        }
        val loop = render(renderer, 4400)
        assertTrue("Complete body/sweat loop must match rest", rest.sameAs(loop))
        rest.recycle()
        loop.recycle()
        empty.recycle()
        emptySweat.recycle()
    }

    @Test
    fun equippedRestFacesKeepCanonicalScaleAndUncoveredHeadGeometry() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val normal =
            requireNotNull(
                LunaFirstFrameCache.getOrLoad(context, LunaActiveAnimation.IDLE, LunaAppearance.NORMAL),
            ).asAndroidBitmap()
        val canonicalSpan = span(pinkCenter(normal, 140, 280, 200, 345), pinkCenter(normal, 405, 280, 470, 345))
        val uncovered = requireNotNull(LunaSickArtworkCache.getOrLoad(context)).still.asAndroidBitmap()
        for (appearance in LunaAppearance.entries) {
            val rest = requireNotNull(LunaSickArtworkCache.getOrLoad(context, appearance)).still.asAndroidBitmap()
            assertTrue("Expanded scene reserves drawing margins", rest.width == 907 && rest.height == 907)
            if (appearance == LunaAppearance.SUNGLASSES) {
                // Glasses occlude cheeks; their visible-fragment centroids cannot measure head scale.
                for (y in 205 until 235) {
                    for (x in 400 until 550) {
                        assertTrue(
                            "Exposed head geometry must match",
                            rest.getPixel(x, y) == uncovered.getPixel(x, y),
                        )
                    }
                }
            } else {
                val measured = span(pinkCenter(rest, 390, 345, 465, 425), pinkCenter(rest, 615, 475, 700, 560))
                assertTrue(
                    "$appearance keeps canonical face scale under rotation",
                    measured / canonicalSpan in .98..1.02,
                )
            }
            for (edge in 0 until rest.width) {
                assertTrue("Scene must not cut the artwork", Color.alpha(rest.getPixel(edge, 0)) == 0)
                assertTrue("Scene must not cut the artwork", Color.alpha(rest.getPixel(0, edge)) == 0)
                assertTrue("Scene must not cut the artwork", Color.alpha(rest.getPixel(rest.width - 1, edge)) == 0)
            }
        }
    }

    private fun pinkCenter(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): Pair<Double, Double> {
        var count = 0
        var xSum = 0L
        var ySum = 0L
        for (y in top until bottom) {
            for (x in left until right) {
                val c = bitmap.getPixel(x, y)
                if (Color.alpha(c) > 200 &&
                    Color.red(c) > 190 &&
                    Color.green(c) in 91..194 &&
                    Color.blue(c) in 111..224
                ) {
                    count++
                    xSum += x
                    ySum += y
                }
            }
        }
        assertTrue("Cheek landmark must be visible", count > 100)
        return xSum.toDouble() / count to ySum.toDouble() / count
    }

    private fun span(
        a: Pair<Double, Double>,
        b: Pair<Double, Double>,
    ): Double =
        kotlin.math.hypot(
            b.first - a.first,
            b.second - a.second,
        )

    private fun render(
        renderer: LunaSickRenderer,
        ms: Long,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(907, 907, Bitmap.Config.ARGB_8888)
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
