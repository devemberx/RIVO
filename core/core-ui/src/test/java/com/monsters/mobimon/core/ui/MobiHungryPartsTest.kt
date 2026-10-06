package com.monsters.mobimon.core.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
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
    fun allAppearancesLoadMasterDensityPartsWithinMemoryBudget() {
        for (name in listOf("normal", "headphones", "goggles")) {
            val accessory = if (name == "normal") null else "accessory:mobi_$name"
            val art = requireNotNull(MobiHungryArtworkCache.getOrLoad(context, accessory))
            assertEquals("$name master width", 627, art.base.width)
            assertEquals("$name master height", 627, art.base.height)
            assertEquals("$name expression parts", 3, art.face.size)
            assertTrue(
                "$name decoded bitmap budget including expression cache (6 MiB)",
                art.base.allocationByteCount +
                    art.thought.allocationByteCount + art.rumble.allocationByteCount +
                    art.face.sumOf { part -> part.expressions.sumOf { it.allocationByteCount } } <
                    6_291_456,
            )
            assertTrue(context.assets.list("characters/mobi/$name/hungry")!!.none { it.endsWith(".png") })
        }
    }
}
