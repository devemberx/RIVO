package com.monsters.mobimon.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MobiHungryExpressionCacheTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun laterExpressionsNeverModifyPreviouslySubmittedTextures() {
        for (accessory in listOf(null, "accessory:mobi_headphones", "accessory:mobi_goggles")) {
            val artwork = requireNotNull(MobiHungryArtworkCache.getOrLoad(context, accessory))
            val renderer = MobiHungryRenderer(artwork)
            val canvas = SubmittedBitmapCanvas()
            for (time in listOf(2_800_000_000L, 2_810_000_000L, 2_830_000_000L, 3_020_000_000L, 4_700_000_000L)) {
                renderer.draw(canvas, time)
                canvas.submitted.forEach { (bitmap, generation) ->
                    assertEquals(
                        "A submitted expression texture was rewritten for $accessory",
                        generation,
                        bitmap.generationId,
                    )
                }
            }
        }
    }

    @Test
    fun preparedPatchesPreserveEndpointsAndBoundIntermediateColorError() {
        for (accessory in listOf(null, "accessory:mobi_headphones", "accessory:mobi_goggles")) {
            val artwork = requireNotNull(MobiHungryArtworkCache.getOrLoad(context, accessory))
            val bytes = artwork.face.sumOf { part -> part.expressions.sumOf { it.allocationByteCount } }
            assertTrue("Prepared faces must fit within 4 MiB per appearance", bytes < 4_194_304)
            for (part in artwork.face) {
                val count = part.rect.width() * part.rect.height()
                val expected = IntArray(count)
                val actual = IntArray(count)
                val output = Bitmap.createBitmap(part.rect.width(), part.rect.height(), Bitmap.Config.ARGB_8888)
                val canvas = Canvas(output)
                canvas.translate(-part.rect.left.toFloat(), -part.rect.top.toFloat())
                canvas.scale(627f / 256f, 627f / 256f)
                var largestError = 0
                var largestErrorAmount = 0f
                for (step in 0..100) {
                    output.eraseColor(0)
                    val amount = step / 100f
                    part.pixelsAt(amount, expected)
                    part.draw(canvas, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG), amount)
                    output.getPixels(actual, 0, part.rect.width(), 0, 0, part.rect.width(), part.rect.height())
                    var maxError = 0
                    for (i in expected.indices) {
                        for (shift in listOf(0, 8, 16, 24)) {
                            maxError =
                                maxOf(
                                    maxError,
                                    kotlin.math.abs(
                                        ((expected[i] ushr shift) and 255) - ((actual[i] ushr shift) and 255),
                                    ),
                                )
                        }
                    }
                    if (maxError > largestError) {
                        largestError = maxError
                        largestErrorAmount = amount
                    }
                    if (step == 0 || step == 100) assertTrue("Endpoint changed", expected.contentEquals(actual))
                }
                assertTrue(
                    "Morph interpolation error $largestError for $accessory at $largestErrorAmount",
                    largestError <= 8,
                )
                output.recycle()
            }
        }
    }

    private class SubmittedBitmapCanvas : Canvas(Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)) {
        val submitted = mutableMapOf<Bitmap, Int>()

        override fun drawBitmap(
            bitmap: Bitmap,
            src: Rect?,
            dst: RectF,
            paint: Paint?,
        ) {
            submitted.putIfAbsent(bitmap, bitmap.generationId)
            super.drawBitmap(bitmap, src, dst, paint)
        }
    }
}
