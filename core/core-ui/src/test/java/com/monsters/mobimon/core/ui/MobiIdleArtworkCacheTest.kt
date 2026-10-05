package com.monsters.mobimon.core.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MobiIdleArtworkCacheTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun resetCache() = MobiIdleArtworkCache.clear()

    @After
    fun clearCache() = MobiIdleArtworkCache.clear()

    @Test
    fun peeksReturnCachedAndPendingAppearancesWhileDecoding() {
        val goggles = "accessory:mobi_goggles"
        val cached = requireNotNull(MobiIdleArtworkCache.getOrLoad(context, goggles))
        val decodingStarted = CountDownLatch(1)
        val finishDecoding = CountDownLatch(1)
        val delayedContext =
            object : ContextWrapper(context) {
                override fun getAssets(): AssetManager {
                    decodingStarted.countDown()
                    check(finishDecoding.await(5, TimeUnit.SECONDS)) { "Decode was not released" }
                    return context.assets
                }
            }
        val workers = Executors.newFixedThreadPool(2)
        val loading =
            workers.submit<MobiIdleArtwork> {
                requireNotNull(
                    MobiIdleArtworkCache.getOrLoad(delayedContext, null),
                )
            }
        try {
            assertTrue("Loader must reach gesture decoding", decodingStarted.await(5, TimeUnit.SECONDS))
            val peeks =
                workers.submit(
                    Callable {
                        Triple(
                            MobiIdleArtworkCache.peek(goggles),
                            MobiIdleArtworkCache.peek(null),
                            MobiIdleArtworkCache.peek("accessory:mobi_headphones"),
                        )
                    },
                )
            val result = peeks.get(1, TimeUnit.SECONDS)
            assertSame(cached, result.first)
            assertNull(result.second)
            assertNull(result.third)
        } finally {
            finishDecoding.countDown()
            workers.shutdown()
            assertTrue("Cache workers must finish", workers.awaitTermination(5, TimeUnit.SECONDS))
        }
        assertSame(loading.get(), MobiIdleArtworkCache.peek(null))
    }
}
