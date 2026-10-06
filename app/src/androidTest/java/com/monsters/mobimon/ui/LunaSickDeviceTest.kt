package com.monsters.mobimon.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import android.graphics.Color as AndroidColor

@RunWith(AndroidJUnit4::class)
class LunaSickDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun equippedSickLayersKeepFirstPoseMotionAndStatusPriority() {
        val names = listOf("normal", "sunglasses", "hat")
        var motion by mutableStateOf(true)
        var warning by mutableStateOf(true)
        var hungry by mutableStateOf(true)
        var animated by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalMobiMonMotionEnabled provides motion) {
                MobiMonTheme {
                    Row(Modifier.background(Color(0xff24354b))) {
                        names.forEach { name ->
                            Column {
                                Text(name)
                                Box(Modifier.size(320.dp).testTag(name), contentAlignment = Alignment.Center) {
                                    PetAvatar(
                                        modifier = Modifier.size(220.dp),
                                        friendId = "friend:luna",
                                        accessoryId =
                                            when (name) {
                                                "hat" -> "accessory:luna_cap"
                                                "sunglasses" -> "accessory:luna_sunglasses"
                                                else -> null
                                            },
                                        vehicleWarning = warning,
                                        vehicleHungry = hungry,
                                        isAnimated = animated,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitUntil(15_000) {
            compose.mainClock.advanceTimeBy(32)
            compose.onAllNodesWithTag("luna-state-sick").fetchSemanticsNodes().size == 3
        }
        compose.mainClock.advanceTimeBy(240)
        val first = names.map { capture(it, "initial") }
        compose.mainClock.advanceTimeBy(550)
        val breath = names.map { capture(it, "breath") }
        names.indices.forEach { assertFalse("$it advances on the hardware canvas", first[it].sameAs(breath[it])) }
        compose.runOnIdle {
            warning = false
            hungry = false
        }
        compose.mainClock.advanceTimeBy(80)
        compose.onAllNodesWithTag("luna-state-sick").assertCountEquals(3)
        compose.onAllNodesWithTag("luna-state-idle").assertCountEquals(3)
        names.forEach { capture(it, "sick-to-normal-fade").recycle() }
        compose.mainClock.advanceTimeBy(240)
        compose.runOnIdle {
            warning = true
            hungry = true
        }
        compose.mainClock.advanceTimeBy(80)
        names.forEach { capture(it, "normal-to-sick-fade").recycle() }
        compose.mainClock.advanceTimeBy(240)
        compose.runOnIdle { motion = false }
        compose.mainClock.advanceTimeBy(32)
        val reduced = names.map { capture(it, "reduced") }
        compose.mainClock.advanceTimeBy(650)
        val later = names.map { capture(it, "reduced-later") }
        names.indices.forEach { assertFalse("Sick gestures continue without travel", reduced[it].sameAs(later[it])) }
        compose.runOnIdle { animated = false }
        compose.mainClock.advanceTimeBy(32)
        val still = names.map { capture(it, "still") }
        compose.mainClock.advanceTimeBy(750)
        val stillLater = names.map { capture(it, "still-later") }
        names.indices.forEach { assertTrue("Explicit still previews hold rest", still[it].sameAs(stillLater[it])) }
        compose.runOnIdle { warning = false }
        compose.mainClock.advanceTimeBy(32)
        compose.onAllNodesWithTag("luna-state-sick").assertCountEquals(0)
        compose.onAllNodesWithTag("luna-state-hungry").assertCountEquals(3)
        names.forEach { capture(it, "hungry-handoff").recycle() }
        compose.runOnIdle { hungry = false }
        compose.mainClock.advanceTimeBy(32)
        compose.onAllNodesWithTag("luna-state-idle").assertCountEquals(3)
        names.forEachIndexed { index, name ->
            val normal = capture(name, "normal-handoff")
            assertTrue(
                "Normal and sick keep their displayed ground anchor",
                kotlin.math.abs(artworkBottom(normal) - artworkBottom(still[index])) <= 1,
            )
            normal.recycle()
        }
        (first + breath + reduced + later + still + stillLater).forEach(Bitmap::recycle)
    }

    private fun capture(
        name: String,
        pose: String,
    ): Bitmap {
        val bitmap = compose.onNodeWithTag(name).captureToImage().asAndroidBitmap()
        val background = bitmap.getPixel(0, 0)
        for (edge in 0 until bitmap.width) {
            assertTrue("Expanded artwork has edge clearance", bitmap.getPixel(edge, 0) == background)
            assertTrue("Expanded artwork has edge clearance", bitmap.getPixel(edge, bitmap.height - 1) == background)
            assertTrue("Expanded artwork has edge clearance", bitmap.getPixel(0, edge) == background)
            assertTrue("Expanded artwork has edge clearance", bitmap.getPixel(bitmap.width - 1, edge) == background)
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.filesDir, "test-screenshots/luna-sick").apply { mkdirs() }
        File(directory, "$name-$pose.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        return bitmap
    }

    private fun artworkBottom(bitmap: Bitmap): Int {
        val background = bitmap.getPixel(0, 0)
        for (y in bitmap.height - 1 downTo 0) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                if (kotlin.math.abs(AndroidColor.red(pixel) - AndroidColor.red(background)) > 10 ||
                    kotlin.math.abs(AndroidColor.green(pixel) - AndroidColor.green(background)) > 10 ||
                    kotlin.math.abs(AndroidColor.blue(pixel) - AndroidColor.blue(background)) > 10
                ) {
                    return y
                }
            }
        }
        error("Missing character artwork")
    }
}
