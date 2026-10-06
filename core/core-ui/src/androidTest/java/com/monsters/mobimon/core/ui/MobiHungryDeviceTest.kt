package com.monsters.mobimon.core.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises real Android decoding and Canvas; this is not a GPU frame-pacing benchmark. */
@RunWith(AndroidJUnit4::class)
class MobiHungryDeviceTest {
    @Test
    fun nativeRendererPreservesEquippedLoopAndProducesDistinctHungryPoses() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val appearances = listOf(null, "accessory:mobi_headphones", "accessory:mobi_goggles")
        val captures = File(context.getExternalFilesDir(null), "mobi-hungry").apply { mkdirs() }
        var previous: Bitmap? = null
        for (accessory in appearances) {
            val artwork = requireNotNull(MobiHungryArtworkCache.getOrLoad(context, accessory))
            val renderer = MobiHungryRenderer(artwork)

            fun render(time: Long): Bitmap =
                Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888).also {
                    val canvas = Canvas(it)
                    canvas.scale(2.5f, 2.5f)
                    renderer.draw(canvas, time * 1_000_000)
                }
            val first = render(0)
            assertTrue(first.sameAs(render(6000)))
            val hungry = render(MobiHungryTimeline.STILL_MS)
            assertFalse(first.sameAs(hungry))
            previous?.let { assertFalse(it.sameAs(hungry)) }
            previous = hungry
            File(captures, "${mobiAppearanceName(accessory)}.png").outputStream().use {
                assertTrue(hungry.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
    }
}
