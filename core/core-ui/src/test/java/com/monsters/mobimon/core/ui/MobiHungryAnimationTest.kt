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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class MobiHungryAnimationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val accessories = listOf(null, "accessory:mobi_headphones", "accessory:mobi_goggles")

    @Test
    fun selectedTimingKeepsCarrotReadableAndTwoShakesContinuous() {
        // Float opacity rounds to one just before the authored plateau (within 3ms).
        val poses = (0L until 6000L).map { MobiHungryTimeline.poseAt(it * 1_000_000 + 500_000) }
        assertEquals(1390.0, poses.count { it.thought == 1f }.toDouble(), 3.0)
        assertEquals(1570.0, poses.count { it.expression == 1f }.toDouble(), 3.0)
        assertEquals(1250.0, poses.count { it.rumble == 1f }.toDouble(), 3.0)
        assertEquals(MobiHungryTimeline.poseAt(0), MobiHungryTimeline.poseAt(6_000_000_000))
        for (time in listOf(3010L, 3900L, 4790L)) {
            for (side in 0..1) {
                val before = MobiHungryTimeline.swayAt(time * 1_000_000 - 10_000, side)
                val after = MobiHungryTimeline.swayAt(time * 1_000_000 + 10_000, side)
                assertEquals(before.x, after.x, .0001f)
                assertEquals(before.y, after.y, .0001f)
                assertEquals(before.degrees, after.degrees, .0001f)
            }
        }
        assertEquals(MobiHungryTimeline.swayAt(3_545_000_000, 0), MobiHungryTimeline.swayAt(4_435_000_000, 0))
    }

    @Test
    fun allAppearancesKeepTheWheelFeetAndLoopSeamFixed() {
        for (accessory in accessories) {
            val art = requireNotNull(MobiHungryArtworkCache.getOrLoad(context, accessory))
            val renderer = MobiHungryRenderer(art)
            val start = render(renderer, 0)
            assertTrue(start.sameAs(render(renderer, 6000)))
            val hungry = render(renderer, 3545)
            assertFalse(start.sameAs(hungry))
            for (y in 205 until 256) {
                for (x in 60 until 200) {
                    assertEquals(
                        "Foot changed for $accessory at $x,$y",
                        start.getPixel(x, y),
                        hungry.getPixel(x, y),
                    )
                }
            }
            for (y in 158 until 210) {
                for (x in 100 until 154) {
                    val distance = (x - 127.5) * (x - 127.5) / (40 * 40) + (y - 186.0) * (y - 186.0) / (35 * 35)
                    if (distance < .8) {
                        // Subpixel head sampling under the translucent source wheel may round by two levels.
                        val a = start.getPixel(x, y)
                        val b = hungry.getPixel(x, y)
                        for (shift in listOf(0, 8, 16, 24)) {
                            assertTrue(
                                kotlin.math.abs(((a ushr shift) and 255) - ((b ushr shift) and 255)) <= 2,
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun faceTransitionPreservesGogglesRimAndNeverChangesEquipmentPixels() {
        for (accessory in accessories) {
            val art = requireNotNull(MobiHungryArtworkCache.getOrLoad(context, accessory))
            art.face.forEach { part ->
                val pixels = IntArray(part.rect.width() * part.rect.height())
                part.pixelsAt(0f, pixels)
                assertTrue(pixels.contentEquals(part.sources[0]))
                part.pixelsAt(1f, pixels)
                assertTrue(pixels.contentEquals(part.sources[1]))
                if (accessory == "accessory:mobi_goggles") assertTrue(part.destination.top >= 107f)
            }
            val renderer = MobiHungryRenderer(art)
            for (time in listOf(2835L, 3020L, 3545L)) {
                val actual = render(renderer, time)
                val expected = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(expected)
                canvas.translate(0f, MobiHungryTimeline.poseAt(time * 1_000_000).dip)
                canvas.drawBitmap(
                    art.base,
                    null,
                    android.graphics.RectF(0f, 0f, 256f, 256f),
                    Paint(
                        Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG,
                    ),
                )
                for (y in 20 until 103) {
                    for (x in 0 until 197) {
                        assertEquals(
                            "Item must move rigidly with head: $accessory ($x,$y)",
                            expected.getPixel(x, y),
                            actual.getPixel(x, y),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun fullBodyRetainsIdleMasterPixelsOutsideAuthoredMouthAndRepair() {
        for (accessory in accessories) {
            val art = requireNotNull(MobiHungryArtworkCache.getOrLoad(context, accessory))
            val fitted = requireNotNull(MobiSpriteCache.firstFrame(context, accessory)).asAndroidBitmap()
            assertEquals(627, art.base.width)
            assertEquals(627, art.base.height)
            for (y in 0 until 627) {
                for (x in 0 until 627) {
                    if (x in 271 until 346 && y in 298 until 343) continue
                    if (accessory == "accessory:mobi_headphones" && x in 284..454 && y in 116..171) continue
                    assertEquals(
                        "Master detail changed for $accessory at $x,$y",
                        fitted.getPixel(x, y),
                        art.base.getPixel(x, y),
                    )
                }
            }
        }
    }

    @Test
    fun crouchWaitsForCarrotFadeAndRecoversBeforeLoop() {
        for (ms in 0L..2640L) {
            val pose = MobiHungryTimeline.poseAt(ms * 1_000_000)
            assertEquals(0f, pose.dip, 0f)
            assertEquals(0f, pose.expression, 0f)
        }
        val fading = MobiHungryTimeline.poseAt(2800_000_000L)
        assertTrue(fading.thought in 0.01f..0.99f)
        assertTrue(fading.dip > 0f && fading.expression > 0f)
        assertEquals(2.2f, MobiHungryTimeline.poseAt(3020_000_000L).dip, .0001f)
        assertEquals(0f, MobiHungryTimeline.poseAt(5700_000_000L).dip, .0001f)
    }

    @Test
    fun canvasDensityDoesNotRescaleFacialPartsOrCarrot() {
        val art = requireNotNull(MobiHungryArtworkCache.getOrLoad(context, "accessory:mobi_goggles"))
        for (time in listOf(2000L, 2835L, 3545L)) {
            val images =
                listOf(160, 320).map { density ->
                    Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).also {
                        it.density = density
                        MobiHungryRenderer(art).draw(Canvas(it), time * 1_000_000)
                    }
                }
            assertTrue("Density must not change part geometry", images[0].sameAs(images[1]))
        }
    }

    @Test
    fun cacheNeverReturnsAnotherAccessory() {
        MobiHungryArtworkCache.clear()
        for (accessory in accessories) {
            val art = requireNotNull(MobiHungryArtworkCache.getOrLoad(context, accessory))
            assertTrue(art === MobiHungryArtworkCache.peek(accessory))
            assertNull(MobiHungryArtworkCache.peek(if (accessory == null) "accessory:mobi_goggles" else null))
        }
        MobiHungryArtworkCache.clear()
        assertNull(MobiHungryArtworkCache.peek("accessory:mobi_goggles"))
    }

    @Test
    fun exportActualRendererPosesForEquippedReview() {
        val times = listOf(0L, 2000L, 2640L, 2835L, 3020L, 3545L, 4790L, 5300L)
        val sheet = Bitmap.createBitmap(256 * times.size, 282 * accessories.size * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f }
        val output = File("build/outputs/mobi-hungry-review").apply { mkdirs() }
        accessories.forEachIndexed { index, accessory ->
            val artwork = requireNotNull(MobiHungryArtworkCache.getOrLoad(context, accessory))
            val renderer = MobiHungryRenderer(artwork)
            times.forEachIndexed { column, time ->
                val pose = render(renderer, time)
                for (theme in 0..1) {
                    val row = index + theme * 3
                    val save = canvas.save()
                    canvas.translate(column * 256f, row * 282f)
                    text.color = if (theme == 0) Color.rgb(230, 234, 240) else Color.rgb(20, 26, 36)
                    canvas.drawRect(0f, 0f, 256f, 282f, text)
                    text.color = if (theme == 0) Color.BLACK else Color.WHITE
                    canvas.drawText("${mobiAppearanceName(accessory)} / ${time}ms", 6f, 16f, text)
                    canvas.drawBitmap(pose, 0f, 24f, null)
                    canvas.restoreToCount(save)
                }
                File(output, "${mobiAppearanceName(accessory)}-$time.png").outputStream().use {
                    pose.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
        File(output, "contact-sheet.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertNotNull(sheet)
    }

    private fun render(
        renderer: MobiHungryRenderer,
        ms: Long,
    ): Bitmap =
        Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).also {
            renderer.draw(
                Canvas(it),
                ms * 1_000_000,
            )
        }
}
