package com.monsters.mobimon.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MobiSickArtworkCacheTest {
    @Test
    fun concurrentRenderersShareUnchangedRingPixels() {
        val workers = Executors.newFixedThreadPool(3)
        val rings =
            try {
                val loading = List(3) { workers.submit<Bitmap> { MobiSickRingCache.getOrLoad() } }
                loading.map { it.get(10, TimeUnit.SECONDS) }
            } finally {
                workers.shutdown()
                workers.awaitTermination(10, TimeUnit.SECONDS)
            }
        val ring = rings.first()
        rings.forEach { assertSame(ring, it) }
        assertSame(ring, MobiSickRingCache.peek())
        assertEquals(816, ring.width)
        assertEquals(816, ring.height)
        val originalPixels = ring.copy(Bitmap.Config.ARGB_8888, false)
        val generation = ring.generationId
        val context = ApplicationProvider.getApplicationContext<Context>()
        val star = requireNotNull(MobiDizzyStarsSpriteCache.getOrLoad(context))
        try {
            for (accessory in listOf(null, "accessory:mobi_headphones", "accessory:mobi_goggles")) {
                val body = requireNotNull(MobiCollapsedSpriteCache.getOrLoad(context, accessory))
                val renderer = MobiSickRenderer(body, star, MobiSickArtworkSpec.forAccessory(accessory), ring)
                val first = Bitmap.createBitmap(408, 408, Bitmap.Config.ARGB_8888)
                val later = Bitmap.createBitmap(408, 408, Bitmap.Config.ARGB_8888)
                try {
                    renderer.draw(Canvas(first), 0L)
                    renderer.draw(Canvas(later), 2_200_000_000L)
                    assertFalse("Equipped sick motion must still advance", first.sameAs(later))
                    assertEquals("Drawing must not invalidate the shared texture", generation, ring.generationId)
                    assertTrue("Ring pixels must stay unchanged", originalPixels.sameAs(ring))
                } finally {
                    first.recycle()
                    later.recycle()
                }
            }
        } finally {
            originalPixels.recycle()
        }
    }
}
