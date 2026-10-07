package com.monsters.mobimon.core.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LunaIdleArtworkTest {
    @Test
    fun layersKeepEquipmentAndBodyFixedWhenEyesBlink() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        for (appearance in LunaAppearance.entries) {
            val artwork = requireNotNull(LunaIdleArtworkCache.getOrLoad(context, appearance))
            assertTrue(artwork === LunaIdleArtworkCache.peek(appearance))
            if (appearance == LunaAppearance.HAT) assertNotNull(artwork.sprout) else assertNull(artwork.sprout)
            val closed = artwork.closedEyesBody
            if (appearance == LunaAppearance.SUNGLASSES) {
                assertNull(closed)
            } else {
                requireNotNull(closed)
                val top = if (appearance == LunaAppearance.HAT) 96 else 0
                var eyeChanges = 0
                for (y in 0 until artwork.body.height) {
                    for (x in 0 until artwork.body.width) {
                        val before = artwork.body.getPixel(x, y)
                        val after = closed.getPixel(x, y)
                        if (before != after) {
                            assertTrue(
                                "Only eyes may change: $appearance ($x,$y)",
                                y in (230 + top)..(316 + top) &&
                                    (x in 178..256 || x in 358..436),
                            )
                            eyeChanges++
                        }
                    }
                }
                assertTrue(eyeChanges > 100)
            }
            val renderer = LunaIdleRenderer(artwork)
            for (ms in listOf(0L, 550L, 1050L, 1650L, 2200L)) {
                val bitmap = Bitmap.createBitmap(627, artwork.body.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.scale(.5f, .5f)
                renderer.draw(canvas, ms * 1_000_000L)
                val output = java.io.File("build/reports/luna-idle-layers/${appearance.assetName}-$ms.png")
                output.parentFile?.mkdirs()
                output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                if (ms == 2200L) {
                    val first =
                        android.graphics.BitmapFactory.decodeFile(
                            java.io.File(output.parentFile, "${appearance.assetName}-0.png").path,
                        )
                    assertEquals("Layer poses join at loop seam", true, bitmap.sameAs(first))
                    first.recycle()
                }
                bitmap.recycle()
            }
        }
    }
}
