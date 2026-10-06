package com.monsters.mobimon.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monsters.mobimon.core.ui.MobiMonTheme
import com.monsters.mobimon.core.ui.PetAvatar
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LunaHungryDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun hungrySharesEquipmentAndReturnsToIdleWithoutChangingScale() {
        val names = listOf("normal", "sunglasses", "hat")
        var hungry by mutableStateOf(true)
        var animated by mutableStateOf(true)
        var swapped by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MobiMonTheme {
                Row(Modifier.background(Color(0xff24354b))) {
                    names.forEach { name ->
                        Box(Modifier.testTag(name).padding(horizontal = 40.dp, vertical = 65.dp)) {
                            val item = if (swapped && name == "normal") "hat" else name
                            PetAvatar(
                                modifier = Modifier.size(220.dp),
                                friendId = "friend:luna",
                                accessoryId =
                                    when (item) {
                                        "hat" -> "accessory:luna_cap"
                                        "sunglasses" -> "accessory:luna_sunglasses"
                                        else -> null
                                    },
                                vehicleHungry = hungry,
                                isAnimated = animated,
                            )
                        }
                    }
                }
            }
        }
        awaitFrames()
        val first = names.map { capture(it, "first") }
        compose.mainClock.advanceTimeBy(2000)
        val moved = names.map { capture(it, "thought-and-drool") }
        names.indices.forEach { assertFalse("Hungry layers advance", first[it].sameAs(moved[it])) }
        for ((delta, pose) in listOf(380L to "detached", 200L to "falling", 500L to "drop-gone", 720L to "closed")) {
            compose.mainClock.advanceTimeBy(delta)
            names.forEach { capture(it, pose).recycle() }
        }
        compose.runOnIdle { animated = false }
        compose.mainClock.advanceTimeBy(32)
        val still = names.map { capture(it, "still") }
        compose.mainClock.advanceTimeBy(1000)
        names.indices.forEach {
            val later = capture(names[it], "still-later")
            assertTrue("Explicit still preview is stable", still[it].sameAs(later))
            later.recycle()
        }
        compose.runOnIdle { swapped = true }
        awaitFrames()
        val equipped = capture("normal", "equipped-cap")
        assertFalse("Equipment updates while hungry", still.first().sameAs(equipped))
        compose.runOnIdle { animated = true }
        compose.mainClock.advanceTimeBy(2200)
        names.forEach { capture(it, "before-transition").recycle() }
        compose.runOnIdle { hungry = false }
        compose.mainClock.advanceTimeBy(100)
        names.forEach {
            val transition = capture(it, "transition")
            val background = transition.getPixel(0, 0)
            var thoughtPixels = 0
            for (y in (transition.height * .06f).toInt() until (transition.height * .17f).toInt()) {
                for (x in (transition.width * .62f).toInt() until (transition.width * .92f).toInt()) {
                    if (transition.getPixel(x, y) != background) thoughtPixels++
                }
            }
            assertTrue("The fading thought cloud must not clip at the avatar slot", thoughtPixels > 5)
            transition.recycle()
        }
        compose.mainClock.advanceTimeBy(240)
        compose.onAllNodesWithTag("luna-state-hungry").assertCountEquals(0)
        compose.onAllNodesWithTag("luna-state-idle").assertCountEquals(3)
        names.forEach { capture(it, "idle").recycle() }
        (first + moved + still + equipped).forEach { it.recycle() }
    }

    private fun awaitFrames() {
        compose.waitUntil(15_000) {
            compose.mainClock.advanceTimeBy(32)
            listOf("normal", "sunglasses", "hat").sumOf {
                compose.onAllNodesWithTag("luna-animation-frame-$it", useUnmergedTree = true).fetchSemanticsNodes().size
            } == 3
        }
    }

    private fun capture(
        name: String,
        pose: String,
    ): Bitmap {
        val bitmap = compose.onNodeWithTag(name).captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.filesDir, "test-screenshots/luna-hungry").apply { mkdirs() }
        File(directory, "$name-$pose.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        return bitmap
    }
}
