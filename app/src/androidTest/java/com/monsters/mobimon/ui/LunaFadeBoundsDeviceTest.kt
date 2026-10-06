package com.monsters.mobimon.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monsters.mobimon.core.ui.LocalMobiMonMotionEnabled
import com.monsters.mobimon.core.ui.PetAvatar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LunaFadeBoundsDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun statusRecoveryKeepsCapSproutVisibleDuringFade() {
        var hungry by mutableStateOf(true)
        render {
            PetAvatar(
                Modifier.size(256.dp).testTag("fade-slot"),
                friendId = "friend:luna",
                accessoryId = "accessory:luna_cap",
                vehicleHungry = hungry,
            )
        }
        val slot = compose.onNodeWithTag("fade-slot").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { hungry = false }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(80)
        assertTrue("Cap sprout remains above the slot during status recovery", sproutPixels("status-fading") > 50)
        assertEquals(slot, compose.onNodeWithTag("fade-slot").fetchSemanticsNode().boundsInRoot)
        compose.mainClock.advanceTimeBy(240)
        assertTrue(sproutPixels("status-opaque") > 50)
    }

    @Test
    fun appearanceHandoffKeepsCapSproutVisibleDuringFade() {
        var appearing by mutableStateOf(true)
        render {
            PetAvatar(
                Modifier.size(256.dp).testTag("fade-slot"),
                friendId = "friend:luna",
                accessoryId = "accessory:luna_cap",
                isAppearing = appearing,
                onAppeared = { appearing = false },
            )
        }
        compose.waitUntil(10_000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithTag("luna-animation-frame-hat").fetchSemanticsNodes().isNotEmpty()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(1_504)
        compose.runOnIdle { assertTrue("Appearance is still fading", appearing) }
        assertTrue("Cap sprout remains above the slot during appearance", sproutPixels("appearance-fading") > 50)
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle { assertTrue(!appearing) }
        assertTrue(sproutPixels("appearance-opaque") > 50)
    }

    private fun render(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalMobiMonMotionEnabled provides true) {
                Box(Modifier.fillMaxSize().background(Color.Black).testTag("fade-root")) {
                    Box(Modifier.padding(top = 80.dp)) { content() }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun sproutPixels(name: String): Int {
        val root = compose.onNodeWithTag("fade-root")
        val origin = root.fetchSemanticsNode().boundsInRoot.topLeft
        val slot =
            compose
                .onNodeWithTag("fade-slot")
                .fetchSemanticsNode()
                .boundsInRoot
                .translate(-origin)
        val bitmap = root.captureToImage().asAndroidBitmap()
        var count = 0
        for (y in slot.top.toInt() - 32 until slot.top.toInt()) {
            for (x in (slot.left + slot.width * .4f).toInt() until (slot.left + slot.width * .75f).toInt()) {
                val pixel = bitmap.getPixel(x, y)
                if (android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) +
                    android.graphics.Color.blue(pixel) > 80
                ) {
                    count++
                }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory =
            InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
                ?: context.getExternalFilesDir("luna-fade-bounds")
        val output = File(directory, "luna-fade-$name.png")
        output.parentFile?.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return count
    }
}
