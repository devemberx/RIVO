package com.monsters.mobimon.core.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "mdpi")
class LunaAppearLoadingTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun pendingEntranceKeepsEmptySlotUntilAtlasLoads() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val decodingStarted = CountDownLatch(1)
        val releaseDecoding = CountDownLatch(1)
        val delayedContext =
            object : ContextWrapper(context) {
                override fun getApplicationContext(): Context = this

                override fun getAssets(): AssetManager {
                    if (Thread.currentThread() != Looper.getMainLooper().thread) {
                        decodingStarted.countDown()
                        check(releaseDecoding.await(10, TimeUnit.SECONDS)) { "Decode was not released" }
                    }
                    return context.assets
                }
            }
        lateinit var view: View
        var completions = 0
        try {
            compose.setContent {
                CompositionLocalProvider(LocalContext provides delayedContext) {
                    val currentView = LocalView.current
                    SideEffect { view = currentView }
                    LunaAppearAnimation(
                        Modifier.size(124.dp).testTag("entrance-slot"),
                        LunaAppearance.NORMAL,
                        onFinished = { completions++ },
                    )
                }
            }
            compose.waitUntil(5_000) { decodingStarted.count == 0L }
            compose.mainClock.autoAdvance = false
            val slot = compose.onNodeWithTag("entrance-slot")
            slot.assertWidthIsEqualTo(124.dp).assertHeightIsEqualTo(124.dp)
            val bounds = slot.fetchSemanticsNode().boundsInRoot
            var visiblePixels = 0
            compose.runOnIdle {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                try {
                    view.draw(Canvas(bitmap))
                    for (y in bounds.top.toInt() until bounds.bottom.toInt()) {
                        for (x in bounds.left.toInt() until bounds.right.toInt()) {
                            if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 0) visiblePixels++
                        }
                    }
                } finally {
                    bitmap.recycle()
                }
                assertEquals("Entrance must not show the seated pose before its empty first frame", 0, visiblePixels)
                assertEquals("Loading must not finish the entrance", 0, completions)
            }
        } finally {
            releaseDecoding.countDown()
        }
        compose.waitUntil(10_000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithTag("luna-appear-atlas-normal").fetchSemanticsNodes().isNotEmpty()
        }
        compose.mainClock.advanceTimeBy(1_800)
        compose.runOnIdle { assertEquals(1, completions) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle { assertEquals(1, completions) }
    }

    @Test
    fun failedAtlasDecodeKeepsIdleFallbackAndFinishesOnce() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val missingAtlasContext =
            object : ContextWrapper(context) {
                override fun getApplicationContext(): Context = this

                override fun getAssets(): AssetManager {
                    if (Thread.currentThread() != Looper.getMainLooper().thread) {
                        throw java.io.IOException("Atlas unavailable")
                    }
                    return context.assets
                }
            }
        var completions = 0
        compose.setContent {
            CompositionLocalProvider(LocalContext provides missingAtlasContext) {
                LunaAppearAnimation(Modifier.size(124.dp), LunaAppearance.NORMAL) { completions++ }
            }
        }
        compose.waitUntil(5_000) { completions == 1 }
        compose.onNodeWithTag("luna-animation-frame-normal").assertExists()
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle { assertEquals(1, completions) }
    }
}
