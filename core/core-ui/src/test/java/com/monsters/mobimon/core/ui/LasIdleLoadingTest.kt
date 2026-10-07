package com.monsters.mobimon.core.ui

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
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
class LasIdleLoadingTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun coldIdleDecodesAwayFromTheUiThread() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val read = AtomicInteger()
        val release = CountDownLatch(1)
        var visible by mutableStateOf(true)
        val readOnMain = AtomicBoolean(false)
        val resources =
            object : Resources(context.assets, context.resources.displayMetrics, context.resources.configuration) {
                override fun openRawResource(
                    id: Int,
                    value: TypedValue,
                ): InputStream {
                    if (id == R.drawable.pet_las_normal_preview) {
                        read.incrementAndGet()
                        if (Thread.currentThread() == Looper.getMainLooper().thread) {
                            readOnMain.set(true)
                        } else {
                            check(release.await(10, TimeUnit.SECONDS))
                        }
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
                if (visible) LasIdleAnimation(Modifier.size(180.dp), animate = false)
            }
        }
        try {
            compose.waitUntil(10_000) { read.get() > 0 }
            assertFalse("Las resource decoding and preparation must run away from main", readOnMain.get())
            compose.onNodeWithTag("las-idle-loading").assertWidthIsEqualTo(180.dp).assertHeightIsEqualTo(180.dp)
            // The UI can remove a card while its worker is blocked.
            compose.runOnIdle { visible = false }
            compose.onNodeWithTag("las-idle-loading").assertDoesNotExist()
            compose.runOnIdle { visible = true }
        } finally {
            release.countDown()
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("las-idle-ready").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("Concurrent and returning cards share the source", 1, read.get())
        val prepared = LasIdleArtworkCache.peek()
        assertTrue("Preparation must finish before the animated canvas appears", prepared != null)
        compose.runOnIdle { visible = false }
        compose.runOnIdle { visible = true }
        compose.onNodeWithTag("las-idle-ready").assertExists()
        assertTrue("Re-entry must reuse prepared layers", prepared === LasIdleArtworkCache.peek())
        assertEquals(1, read.get())
    }
}
