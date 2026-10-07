package com.monsters.mobimon.core.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w600dp-h400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class LunaFadeBoundsTest {
    // Queue IO decode completions on the test clock instead of resuming composition on the IO worker.
    @get:Rule val compose = createComposeRule(effectContext = StandardTestDispatcher())
    private lateinit var view: View

    @Test
    fun statusRecoveryKeepsCapSproutVisibleBeforeFadeCompletes() {
        LunaIdleArtworkCache.getOrLoad(ApplicationProvider.getApplicationContext())
        var hungry by mutableStateOf(true)
        render {
            PetAvatar(
                Modifier.size(256.dp).testTag("slot"),
                friendId = "friend:luna",
                accessoryId = "accessory:luna_cap",
                vehicleHungry = hungry,
            )
        }
        val slot = compose.onNodeWithTag("slot").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            hungry = false
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(80)
        assertTrue("Incoming cap retains its sprout during the status fade", sproutPixels("status-fading") > 50)
        assertEquals(slot, compose.onNodeWithTag("slot").fetchSemanticsNode().boundsInRoot)
        compose.mainClock.advanceTimeBy(240)
        assertTrue("Opaque cap keeps the same headroom", sproutPixels("status-opaque") > 50)
        assertEquals(slot, compose.onNodeWithTag("slot").fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun appearanceHandoffKeepsCapSproutVisibleBeforeFadeCompletes() {
        var finished = false
        render {
            LunaAppearAnimation(
                Modifier.size(256.dp).testTag("slot"),
                LunaAppearance.HAT,
                onFinished = { finished = true },
            )
        }
        compose.waitUntil(10_000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithTag("luna-appear-atlas-hat").fetchSemanticsNodes().isNotEmpty()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(1_504)
        assertTrue("Handoff is still fading", !finished)
        assertTrue("Incoming cap retains its sprout during the appearance fade", sproutPixels("appearance-fading") > 50)
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle { assertTrue(finished) }
        assertTrue("Opaque cap keeps the same headroom", sproutPixels("appearance-opaque") > 50)
    }

    private fun render(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val current = LocalView.current
            SideEffect { view = current }
            Box(Modifier.padding(top = 80.dp)) { content() }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun sproutPixels(name: String): Int {
        val slot = compose.onNodeWithTag("slot").fetchSemanticsNode().boundsInRoot
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        try {
            var count = 0
            // The fitted sprout extends above the unchanged body slot, to the right of its centre.
            for (y in slot.top.toInt() - 32 until slot.top.toInt()) {
                for (x in (slot.left + slot.width * .4f).toInt() until (slot.left + slot.width * .75f).toInt()) {
                    if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 40) count++
                }
            }
            val output = File("build/reports/luna-fade-bounds/$name.png")
            output.parentFile?.mkdirs()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return count
        } finally {
            bitmap.recycle()
        }
    }
}
