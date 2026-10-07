package com.monsters.mobimon.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
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
import com.monsters.mobimon.core.ui.LocalMobiMonMotionEnabled
import com.monsters.mobimon.core.ui.MobiMonTheme
import com.monsters.mobimon.core.ui.PetAvatar
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Hardware-rendered equipped warning states; no vehicle/provider integration is claimed. */
@RunWith(AndroidJUnit4::class)
class MobiSickDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun stillPreviewsLoadMatchingEquippedFirstFrames() {
        val names = listOf("normal", "headphones", "goggles")
        compose.setContent {
            MobiMonTheme {
                Row(Modifier.background(Color(0xff24354b))) {
                    names.forEach { name ->
                        PetAvatar(
                            modifier = Modifier.size(220.dp).testTag(name),
                            friendId = "friend:mobi",
                            accessoryId = if (name == "normal") null else "accessory:mobi_$name",
                            isAnimated = false,
                        )
                    }
                }
            }
        }
        compose.waitUntil(15_000) {
            names.all { name ->
                compose.onAllNodesWithTag("mobi-animation-frame-$name").fetchSemanticsNodes().isNotEmpty()
            }
        }
        val previews = names.map { capture(it, "idle-first-frame") }
        try {
            assertFalse("Headphones must retain their fitted first frame", previews[0].sameAs(previews[1]))
            assertFalse("Goggles must retain their fitted first frame", previews[0].sameAs(previews[2]))
            assertFalse("Equipped appearances must stay distinct", previews[1].sameAs(previews[2]))
        } finally {
            previews.forEach { it.recycle() }
        }
    }

    @Test
    fun equippedWarningKeepsGesturesWhenWanderingIsDisabled() {
        val names = listOf("normal", "headphones", "goggles")
        var motion by mutableStateOf(true)
        var warning by mutableStateOf(true)
        var animated by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalMobiMonMotionEnabled provides motion) {
                MobiMonTheme {
                    Row(Modifier.background(Color(0xff24354b))) {
                        names.forEach { name ->
                            Column {
                                Text(name)
                                PetAvatar(
                                    modifier = Modifier.size(220.dp).testTag(name),
                                    friendId = "friend:mobi",
                                    accessoryId = if (name == "normal") null else "accessory:mobi_$name",
                                    vehicleWarning = warning,
                                    isAnimated = animated,
                                    vehicleHungry = true,
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.waitUntil(15_000) {
            compose.mainClock.advanceTimeBy(32)
            compose.onAllNodesWithTag("mobi-sick-layer").fetchSemanticsNodes().size == 3
        }
        compose.mainClock.advanceTimeBy(240)
        val initial = names.map { capture(it, "initial") }
        compose.mainClock.advanceTimeBy(1650)
        names.forEach { capture(it, "opening") }
        compose.mainClock.advanceTimeBy(550)
        val opened = names.map { capture(it, "open") }
        names.indices.forEach {
            assertFalse(
                "${names[it]} advances on the hardware canvas",
                initial[it].sameAs(opened[it]),
            )
        }
        compose.mainClock.advanceTimeBy(1400)
        names.forEach { capture(it, "closing") }
        compose.runOnIdle {
            motion = false
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(32)
        val held = names.map { capture(it, "reduced") }
        compose.mainClock.advanceTimeBy(10_000)
        names.indices.forEach {
            assertFalse("Disabling wandering keeps sick gestures", held[it].sameAs(capture(names[it], "reduced-later")))
        }
        compose.runOnIdle {
            animated = false
            Snapshot.sendApplyNotifications()
        }
        // With a paused clock, nested subcompositions need frames before the still pose reaches draw.
        var static = emptyList<Bitmap>()
        compose.waitUntil(15_000) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val current = names.map { capture(it, "static-preview") }
            val settled = static.size == current.size && static.indices.all { static[it].sameAs(current[it]) }
            static.forEach { it.recycle() }
            static = current
            settled
        }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        names.indices.forEach {
            assertTrue("Explicit still previews remain static", static[it].sameAs(capture(names[it], "static-later")))
        }
        compose.runOnIdle {
            warning = false
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(32)
        compose.onAllNodesWithTag("mobi-sick-layer").assertCountEquals(0)
        compose.onAllNodesWithTag("mobi-hungry-layer").assertCountEquals(3)
        (initial + opened + held + static).forEach { it.recycle() }
    }

    private fun capture(
        name: String,
        pose: String,
    ): Bitmap {
        val bitmap = compose.onNodeWithTag(name).captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.filesDir, "test-screenshots/mobi-sick").apply { mkdirs() }
        File(directory, "$name-$pose.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        return bitmap
    }
}
