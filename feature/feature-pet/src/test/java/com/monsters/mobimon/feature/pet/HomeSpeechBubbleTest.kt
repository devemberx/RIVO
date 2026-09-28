package com.monsters.mobimon.feature.pet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import com.monsters.mobimon.core.ui.LocalMobiMonMotionEnabled
import com.monsters.mobimon.core.ui.MobiMonTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-rKR-w500dp-h300dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeSpeechBubbleTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View
    private val motion = mutableStateOf(true)
    private val visible = mutableStateOf(true)
    private val revision = mutableStateOf(0)

    @Test
    fun entranceAnimatesOnceAndReplaysOnlyAfterReentry() {
        show()
        val entering = pixels()
        compose.mainClock.advanceTimeBy(120)
        val middle = pixels()
        compose.mainClock.advanceTimeBy(900)
        val settled = pixels()
        assertNotEquals("Bubble must animate on entry", entering, middle)
        assertNotEquals("Bubble must settle after its pop", middle, settled)
        update { revision.value++ }
        compose.mainClock.advanceTimeBy(500)
        assertEquals("Unrelated state must not restart the entrance", settled, pixels())
        update { visible.value = false }
        update { visible.value = true }
        assertNotEquals("Returning Home starts a new entrance", settled, pixels())
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("home-companion-message").fetchSemanticsNode()
    }

    @Test
    fun reducedMotionDisplaysImmediatelyAndDoesNotReplayWhenEnabled() {
        motion.value = false
        show()
        val first = pixels()
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(first, pixels())
        update { motion.value = true }
        compose.mainClock.advanceTimeBy(200)
        val enabled = pixels()
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(enabled, pixels())
    }

    @Test
    fun enablingReducedMotionDuringEntranceSnapsToSettledBubble() {
        show()
        compose.mainClock.advanceTimeBy(80)
        val entering = pixels()
        update { motion.value = false }
        compose.mainClock.advanceTimeBy(32)
        val stopped = pixels()
        assertNotEquals(entering, stopped)
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(stopped, pixels())
    }

    @Test
    @Config(qualifiers = "ko-rKR-w800dp-h600dp-mdpi")
    fun enlargedTextLetsTheSpeechBubbleFollowItsContent() {
        motion.value = false
        show(fontScale = 1.5f)
        val bounds = compose.onNodeWithTag("home-companion-message").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width in 390f..800f)
        assertTrue(bounds.height >= 210f)
    }

    private fun show(fontScale: Float = 1f) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val current = LocalView.current
            SideEffect { view = current }
            CompositionLocalProvider(
                LocalMobiMonMotionEnabled provides motion.value,
                LocalDensity provides Density(1f, fontScale),
            ) {
                MobiMonTheme {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        // Read changing host state without changing the bubble's identity.
                        if (visible.value && revision.value >= 0) HomeSpeechBubble()
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun update(block: () -> Unit) {
        compose.runOnIdle {
            block()
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun pixels(): List<Int> {
        lateinit var pixels: List<Int>
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            pixels =
                IntArray(
                    view.width * view.height,
                ).also { bitmap.getPixels(it, 0, view.width, 0, 0, view.width, view.height) }.toList()
            bitmap.recycle()
        }
        return pixels
    }
}
