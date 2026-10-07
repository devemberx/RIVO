package com.monsters.mobimon.core.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.os.Looper
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LunaSickLoadingTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun clearArtwork() {
        LunaSickArtworkCache.clear()
    }

    @Test
    fun coldAnimatedArtworkReadsAssetsAwayFromTheUiThread() {
        assertColdLoading(animateFrames = true)
    }

    @Test
    fun coldStillPreviewReadsAssetsAwayFromTheUiThread() {
        assertColdLoading(animateFrames = false)
    }

    private fun assertColdLoading(animateFrames: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val readOnMain = AtomicBoolean(false)
        val readOnWorker = AtomicBoolean(false)
        val guardedContext =
            object : ContextWrapper(context) {
                override fun getApplicationContext(): Context = this

                override fun getAssets(): AssetManager {
                    if (Thread.currentThread() == Looper.getMainLooper().thread) {
                        readOnMain.set(true)
                    } else {
                        readOnWorker.set(true)
                    }
                    return context.assets
                }
            }
        compose.setContent {
            CompositionLocalProvider(LocalContext provides guardedContext) {
                LunaSickAnimation(Modifier.size(180.dp), animateFrames = animateFrames)
            }
        }
        awaitFrame("normal")
        assertFalse("Sick artwork preparation must not read assets on the UI thread", readOnMain.get())
        assertTrue("Cold sick artwork must actually load on a worker", readOnWorker.get())
    }

    @Test
    fun pendingEquipmentKeepsItsSlotAndCachedEquipmentRemainsResponsive() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val decodingStarted = CountDownLatch(1)
        val releaseDecoding = CountDownLatch(1)
        val delayDecoding = AtomicBoolean(false)
        val delayedContext = delayedContext(context, delayDecoding, decodingStarted, releaseDecoding)
        var appearance by mutableStateOf(LunaAppearance.NORMAL)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides delayedContext) {
                LunaSickAnimation(Modifier.size(180.dp), appearance = appearance)
            }
        }
        awaitFrame("normal")
        try {
            delayDecoding.set(true)
            compose.runOnIdle {
                appearance = LunaAppearance.HAT
                Snapshot.sendApplyNotifications()
            }
            compose.waitUntil(5_000) { decodingStarted.count == 0L }
            compose
                .onNodeWithTag("luna-animation-loading-hat")
                .assertExists()
                .assertWidthIsEqualTo(180.dp)
                .assertHeightIsEqualTo(180.dp)
            compose.onNodeWithTag("luna-animation-frame-normal").assertDoesNotExist()
            compose.runOnIdle {
                appearance = LunaAppearance.NORMAL
                Snapshot.sendApplyNotifications()
            }
            compose.onNodeWithTag("luna-animation-frame-normal").assertExists()
            compose.onNodeWithTag("luna-animation-frame-hat").assertDoesNotExist()
        } finally {
            releaseDecoding.countDown()
        }
        compose.waitUntil(5_000) { LunaSickArtworkCache.peek(LunaAppearance.HAT) != null }
        compose.onNodeWithTag("luna-animation-frame-normal").assertExists()
        compose.onNodeWithTag("luna-animation-frame-hat").assertDoesNotExist()
    }

    @Test
    fun cacheInvalidationDoesNotWaitForDecodeOrRetainItsLateResult() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val decodingStarted = CountDownLatch(1)
        val releaseDecoding = CountDownLatch(1)
        val delayedContext = delayedContext(context, AtomicBoolean(true), decodingStarted, releaseDecoding)
        val executor = Executors.newFixedThreadPool(2)
        val queuedLoading = FutureTask { LunaSickArtworkCache.getOrLoad(context, LunaAppearance.HAT) }
        val queuedThread = Thread(queuedLoading, "queued-luna-sick-load")
        try {
            val loading = executor.submit<LunaSickArtwork?> { LunaSickArtworkCache.getOrLoad(delayedContext) }
            assertTrue("The worker must enter a real decode", decodingStarted.await(5, TimeUnit.SECONDS))
            queuedThread.start()
            compose.waitUntil(5_000) { queuedThread.state == Thread.State.BLOCKED }
            val clearing = executor.submit { LunaSickArtworkCache.clear() }
            try {
                clearing.get(1, TimeUnit.SECONDS)
            } finally {
                releaseDecoding.countDown()
            }
            assertNotNull(loading.get(5, TimeUnit.SECONDS))
            assertNotNull(queuedLoading.get(5, TimeUnit.SECONDS))
            assertNull("An invalidated decode must not repopulate the active cache", LunaSickArtworkCache.peek())
            assertNull(
                "A queued invalidated decode must not repopulate the active cache",
                LunaSickArtworkCache.peek(LunaAppearance.HAT),
            )
            assertNotNull("A new generation must load normally", LunaSickArtworkCache.getOrLoad(context))
        } finally {
            releaseDecoding.countDown()
            queuedThread.join(5_000)
            assertFalse("The queued decoder must finish", queuedThread.isAlive)
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    private fun delayedContext(
        context: Context,
        delayDecoding: AtomicBoolean,
        decodingStarted: CountDownLatch,
        releaseDecoding: CountDownLatch,
    ): Context =
        object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this

            override fun getAssets(): AssetManager {
                if (delayDecoding.get() && Thread.currentThread() != Looper.getMainLooper().thread) {
                    decodingStarted.countDown()
                    check(releaseDecoding.await(10, TimeUnit.SECONDS)) { "Decode was not released" }
                }
                return context.assets
            }
        }

    private fun awaitFrame(appearance: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("luna-animation-frame-$appearance").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
