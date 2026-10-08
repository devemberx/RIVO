package com.monsters.mobimon.feature.customization

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.os.Looper
import android.util.TypedValue
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.monsters.mobimon.core.ui.MobiMonTheme
import com.monsters.mobimon.core.ui.companionBackgroundRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
@Suppress("DEPRECATION")
class StoreBackgroundLoadingTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun coldStorePreviewDecodesAwayFromTheUiThread() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val background = companionBackgroundRes("Night")
        val read = AtomicBoolean(false)
        val readOnMain = AtomicBoolean(false)
        val resources =
            object : Resources(context.assets, context.resources.displayMetrics, context.resources.configuration) {
                override fun openRawResource(
                    id: Int,
                    value: TypedValue,
                ): InputStream {
                    if (id == background) {
                        read.set(true)
                        if (Thread.currentThread() == Looper.getMainLooper().thread) readOnMain.set(true)
                    }
                    return super.openRawResource(id, value)
                }
            }
        val guarded =
            object : ContextWrapper(context) {
                override fun getResources(): Resources = resources
            }
        compose.setContent {
            CompositionLocalProvider(LocalContext provides guarded) {
                MobiMonTheme {
                    CustomizationScreen(
                        inventory = null,
                        catalog = emptyList(),
                        selectedItemId = null,
                        purchasing = false,
                        purchaseFailed = false,
                        onSelectItem = {},
                        onPurchaseItem = { _, _ -> },
                        onEquipItem = {},
                        onEquipFriend = {},
                        pointBalance = 0,
                        pointLoadFailed = false,
                        timeOfDay = "Night",
                    )
                }
            }
        }
        compose.waitUntil(10_000) { read.get() }
        assertFalse("Store backgrounds must not decode during composition", readOnMain.get())
    }

    @Test
    fun switchingPendingBackgroundDiscardsLateResultAndReusesCachedImages() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val night = companionBackgroundRes("Night")
        val day = companionBackgroundRes("Day")
        val nightStarted = CountDownLatch(1)
        val dayStarted = CountDownLatch(1)
        val releaseNight = CountDownLatch(1)
        val releaseDay = CountDownLatch(1)
        val reads = AtomicInteger()
        val resources =
            object : Resources(context.assets, context.resources.displayMetrics, context.resources.configuration) {
                override fun openRawResource(
                    id: Int,
                    value: TypedValue,
                ): InputStream {
                    reads.incrementAndGet()
                    if (id == night) {
                        nightStarted.countDown()
                        check(releaseNight.await(10, TimeUnit.SECONDS))
                    } else if (id == day) {
                        dayStarted.countDown()
                        check(releaseDay.await(10, TimeUnit.SECONDS))
                    }
                    return super.openRawResource(id, value)
                }
            }
        val guarded =
            object : ContextWrapper(context) {
                override fun getResources(): Resources = resources
            }
        var selected by mutableStateOf(night)
        try {
            compose.setContent {
                CompositionLocalProvider(LocalContext provides guarded) {
                    StoreBackgroundImage(selected, Modifier.size(180.dp).testTag("slot"))
                }
            }
            compose.waitUntil(5_000) { nightStarted.count == 0L }
            compose.onNodeWithTag("slot").assertWidthIsEqualTo(180.dp).assertHeightIsEqualTo(180.dp)
            compose.runOnIdle { selected = day }
            releaseNight.countDown()
            compose.waitUntil(5_000) { dayStarted.count == 0L }
            assertNotNull(StoreBackgroundCache.peek(resources, night))
            compose.onNodeWithTag("store-background-ready-$night", useUnmergedTree = true).assertDoesNotExist()
            releaseDay.countDown()
            compose.waitUntil(5_000) {
                compose
                    .onAllNodesWithTag(
                        "store-background-ready-$day",
                        useUnmergedTree = true,
                    ).fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose.runOnIdle { selected = night }
            compose.onNodeWithTag("store-background-ready-$night", useUnmergedTree = true).assertExists()
            compose.onNodeWithTag("store-background-ready-$day", useUnmergedTree = true).assertDoesNotExist()
            assertEquals("Changing back to a cached scene must not decode again", 2, reads.get())
        } finally {
            releaseNight.countDown()
            releaseDay.countDown()
        }
    }

    @Test
    fun backgroundCacheEvictsOldScenesWithoutRecyclingDisplayedImages() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resources = context.resources
        val night = companionBackgroundRes("Night")
        val day = companionBackgroundRes("Day")
        val morning = companionBackgroundRes("Morning")
        val displayed = requireNotNull(StoreBackgroundCache.load(resources, night))
        assertNotNull(StoreBackgroundCache.load(resources, day))
        assertNotNull(StoreBackgroundCache.load(resources, morning))
        assertNull("Do not retain all seven full-resolution periods", StoreBackgroundCache.peek(resources, night))
        assertFalse("An evicted bitmap can still be held by a displayed image", displayed.isRecycled)
        assertNotNull(StoreBackgroundCache.peek(resources, day))
        assertNotNull(StoreBackgroundCache.peek(resources, morning))
    }
}
