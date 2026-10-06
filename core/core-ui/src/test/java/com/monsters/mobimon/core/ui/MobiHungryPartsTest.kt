package com.monsters.mobimon.core.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.FileNotFoundException
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MobiHungryPartsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun missingPartsReturnToFallbackWithoutPoisoningTheCache() {
        MobiHungryArtworkCache.clear()
        val unavailable =
            object : ContextWrapper(context) {
                override fun getAssets(): AssetManager = throw FileNotFoundException("Unavailable packaged part")
            }
        assertNull(MobiHungryArtworkCache.getOrLoad(unavailable, null))
        assertNull(MobiHungryArtworkCache.peek(null))
        assertTrue(MobiHungryArtworkCache.getOrLoad(context, null) != null)
    }

    @Test
    fun webpPartsMatchReviewedNativeLoopAtEvery25Milliseconds() {
        // Reviewed full-resolution body/equipment and expression parts. The six-second timeline
        // and effect assets are unchanged; 723 samples pin all appearances and the loop seam.
        val expected =
            mapOf(
                "normal" to "d732b0b1e284e849af8268956d7546da0bf65fcd025fa750b571a79ddfe00d62",
                "headphones" to "4e76e3094aeef56d22b92279ce294d2f02eff56e7e814bce39fb5904ad5aeec4",
                "goggles" to "acf4dea7d407f83202384d5deae11178dcbb7806fa7c3be1377a38aeaa0214ca",
            )
        for ((name, hash) in expected) {
            val accessory = if (name == "normal") null else "accessory:mobi_$name"
            val art = requireNotNull(MobiHungryArtworkCache.getOrLoad(context, accessory))
            val renderer = MobiHungryRenderer(art)
            val digest = MessageDigest.getInstance("SHA-256")
            val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(256 * 256)
            for (ms in 0L..6000L step 25) {
                bitmap.eraseColor(Color.TRANSPARENT)
                renderer.draw(Canvas(bitmap), ms * 1_000_000)
                bitmap.getPixels(pixels, 0, 256, 0, 0, 256, 256)
                for (pixel in pixels) {
                    digest.update((pixel ushr 24).toByte())
                    digest.update((pixel ushr 16).toByte())
                    digest.update((pixel ushr 8).toByte())
                    digest.update(pixel.toByte())
                }
            }
            assertEquals(name, hash, digest.digest().joinToString("") { "%02x".format(it) })
            assertTrue(
                art.base.allocationByteCount +
                    art.thought.allocationByteCount + art.rumble.allocationByteCount <
                    1_900_000,
            )
            assertTrue(context.assets.list("characters/mobi/$name/hungry")!!.none { it.endsWith(".png") })
            bitmap.recycle()
        }
    }
}
