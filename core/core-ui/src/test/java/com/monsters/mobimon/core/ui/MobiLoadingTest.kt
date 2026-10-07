package com.monsters.mobimon.core.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MobiLoadingTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun coldStillPreviewReadsAssetsAwayFromTheUiThread() {
        MobiSpriteCache.clear()
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
                NormalMobiIdleAnimation(accessoryId = "accessory:mobi_goggles", animateFrames = false)
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("mobi-animation-frame-goggles").fetchSemanticsNodes().isNotEmpty()
        }
        assertFalse("Cold preview decoding must not read assets on the UI thread", readOnMain.get())
        assertTrue("The cold preview must actually load its image on a worker", readOnWorker.get())
    }

    @Test
    fun pendingEquipmentDoesNotBlockUiOrDisplayThePreviousItem() {
        MobiSpriteCache.clear()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val decodingStarted = CountDownLatch(1)
        val releaseDecoding = CountDownLatch(1)
        val delayDecoding = AtomicBoolean(false)
        val delayedContext =
            object : ContextWrapper(context) {
                override fun getApplicationContext(): Context = this

                override fun getAssets(): AssetManager {
                    if (delayDecoding.get()) {
                        decodingStarted.countDown()
                        check(releaseDecoding.await(10, TimeUnit.SECONDS)) { "Decode was not released" }
                    }
                    return context.assets
                }
            }
        var accessory by mutableStateOf<String?>(null)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides delayedContext) {
                NormalMobiIdleAnimation(accessoryId = accessory, animateFrames = false)
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("mobi-animation-frame-normal").fetchSemanticsNodes().isNotEmpty()
        }
        try {
            delayDecoding.set(true)
            compose.runOnIdle {
                accessory = "accessory:mobi_goggles"
                Snapshot.sendApplyNotifications()
            }
            compose.waitForIdle()
            compose.waitUntil(5_000) { decodingStarted.count == 0L }
            compose.onNodeWithTag("mobi-animation-loading-goggles").assertExists()
            compose.onNodeWithTag("mobi-animation-frame-normal").assertDoesNotExist()
            // A cached appearance stays usable while another worker owns the decode lock.
            compose.runOnIdle {
                accessory = null
                Snapshot.sendApplyNotifications()
            }
            compose.onNodeWithTag("mobi-animation-frame-normal").assertExists()
        } finally {
            releaseDecoding.countDown()
        }
    }
}
