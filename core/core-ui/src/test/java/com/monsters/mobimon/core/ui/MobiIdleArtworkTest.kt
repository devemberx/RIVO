package com.monsters.mobimon.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MobiIdleArtworkTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val accessories = listOf(null, "accessory:mobi_headphones", "accessory:mobi_goggles")

    @Test
    fun selectedBlinkTimingDoesNotRestartBodyMotion() {
        assertFalse(MobiIdleTimeline.blinkClosedAt(2_299_999_999L))
        assertTrue(MobiIdleTimeline.blinkClosedAt(2_300_000_000L))
        assertTrue(MobiIdleTimeline.blinkClosedAt(2_439_999_999L))
        assertFalse(MobiIdleTimeline.blinkClosedAt(2_440_000_000L))
        assertTrue(MobiIdleTimeline.blinkClosedAt(7_100_000_000L))
        assertFalse(MobiIdleTimeline.blinkClosedAt(-1L))
        assertEquals(17f, MobiIdleTimeline.sproutShiftAt(1_200_000_000L), 0.0001f)
        assertEquals(-17f, MobiIdleTimeline.sproutShiftAt(3_600_000_000L), 0.0001f)
        assertEquals(0f, MobiIdleTimeline.sproutShiftAt(4_800_000_000L), 0.0001f)
        assertTrue(kotlin.math.abs(MobiIdleTimeline.tiltAt(4_800_000_000L)) > 2f)
    }

    @Test
    fun blinkingChangesOnlyTheEyesAndKeepsTheGogglesRim() {
        accessories.forEach { accessory ->
            val art = requireNotNull(MobiIdleArtworkCache.getOrLoad(context, accessory))
            var changed = 0
            for (y in 0 until 627) {
                for (x in 0 until 627) {
                    if (art.body.getPixel(x, y) != art.closedEyesBody.getPixel(x, y)) {
                        changed++
                        assertTrue(
                            "Blink touched another part: $accessory ($x,$y)",
                            y in 256..325 && (x in 200..268 || x in 348..416),
                        )
                        if (accessory == "accessory:mobi_goggles") assertTrue("Goggles rim erased", y >= 263)
                    }
                }
            }
            assertTrue("Blink must visibly close both eyes for $accessory", changed > 1000)
        }
    }

    @Test
    fun canonicalBodyAndItemFitAreUnchangedOutsideRevealedSproutArea() {
        accessories.forEach { accessory ->
            val art = requireNotNull(MobiIdleArtworkCache.getOrLoad(context, accessory))
            val source = art.original.asAndroidBitmap()
            for (y in 165 until 627) {
                for (x in 0 until 627) {
                    assertEquals(
                        "Canonical fitted body changed: $accessory ($x,$y)",
                        source.getPixel(x, y),
                        art.body.getPixel(x, y),
                    )
                }
            }
        }
    }

    @Test
    fun renderAllEquippedGesturesAtSelectedTimes() {
        val times = listOf(0L, 1200L, 2370L, 3600L, 4800L, 6200L)
        val sheet = Bitmap.createBitmap(256 * times.size, 282 * accessories.size, Bitmap.Config.ARGB_8888)
        val output = Canvas(sheet)
        val label =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 12f
            }
        output.drawColor(Color.rgb(23, 35, 56))
        accessories.forEachIndexed { row, accessory ->
            val art = requireNotNull(MobiIdleArtworkCache.getOrLoad(context, accessory))
            val renderer = MobiIdleRenderer(art)
            val open = Bitmap.createBitmap(627, 627, Bitmap.Config.ARGB_8888)
            val closed = Bitmap.createBitmap(627, 627, Bitmap.Config.ARGB_8888)
            listOf(open to 0L, closed to 2_370_000_000L).forEach { (bitmap, nanos) ->
                val canvas = Canvas(bitmap)
                canvas.scale(0.5f, 0.5f)
                renderer.draw(canvas, nanos)
            }
            assertTrue("Gestures must advance for $accessory", !open.sameAs(closed))
            assertTrue("Open eye must be dark", Color.red(open.getPixel(234, 280)) < 100)
            assertTrue("Blink must cover the original oval", Color.red(closed.getPixel(234, 280)) > 150)
            open.recycle()
            closed.recycle()
            times.forEachIndexed { col, ms ->
                val save = output.save()
                output.translate(col * 256f, row * 282f)
                output.drawText("${mobiAppearanceName(accessory)} / ${ms}ms", 6f, 15f, label)
                output.translate(0f, 22f)
                val nanos = ms * 1_000_000L
                output.translate(128f, 230.4f + 256 * MobiIdleTimeline.liftFractionAt(nanos))
                output.rotate(MobiIdleTimeline.tiltAt(nanos))
                output.scale(MobiIdleTimeline.scaleXAt(nanos), MobiIdleTimeline.scaleYAt(nanos))
                output.translate(-128f, -230.4f)
                output.scale(256f / 1254, 256f / 1254)
                renderer.draw(output, nanos)
                output.restoreToCount(save)
            }
        }
        val file = File("build/reports/mobi-idle-equipped/contact-sheet.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        sheet.recycle()
    }
}
