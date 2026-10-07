package com.monsters.mobimon.ui

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.PetProfile
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.VehicleSnapshot
import com.monsters.mobimon.core.ui.MobiMonTheme
import com.monsters.mobimon.feature.pet.PetHomeScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LunaHungryHomeDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun hungryHomeCapturesEquipmentAndDialogueAtBothTextScales() {
        var accessory by mutableStateOf<String?>(null)
        var fontScale by mutableStateOf(1f)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MobiMonTheme {
                    key(fontScale, accessory) {
                        PetHomeScreen(
                            PetProfile("hungry-layout-review"),
                            VehicleSnapshot(
                                "review",
                                "epoch",
                                1,
                                1000,
                                SignalSource.SIMULATED,
                                DrivingState.PARKED,
                                SignalQuality.VALID,
                                10,
                                timeOfDay = "Sunset",
                            ),
                            {},
                            {},
                            modifier = Modifier.testTag("luna-home-review"),
                            friendId = "friend:luna",
                            accessoryId = accessory,
                            pointBalance = 1200,
                            interactionAllowed = true,
                            connectionAvailable = true,
                        )
                    }
                }
            }
        }
        for (scale in listOf(1f, 2f)) {
            compose.runOnIdle { fontScale = scale }
            for ((name, item) in listOf(
                "normal" to null,
                "sunglasses" to "accessory:luna_sunglasses",
                "hat" to "accessory:luna_cap",
            )) {
                compose.runOnIdle { accessory = item }
                compose.waitUntil(15_000) {
                    compose.mainClock.advanceTimeBy(32)
                    compose
                        .onAllNodesWithTag(
                            "luna-animation-frame-$name",
                            useUnmergedTree = true,
                        ).fetchSemanticsNodes()
                        .isNotEmpty()
                }
                // Restart each scene so the greeting and dialogue are both visible at the same gesture phase.
                compose.mainClock.advanceTimeBy(2200)
                if (scale > 1f) compose.onNodeWithTag("luna-state-hungry", useUnmergedTree = true).performScrollTo()
                val avatar =
                    compose
                        .onNodeWithTag(
                            "luna-state-hungry",
                            useUnmergedTree = true,
                        ).fetchSemanticsNode()
                        .boundsInRoot
                val greeting = compose.onNodeWithTag("home-ambient-text").fetchSemanticsNode().boundsInRoot
                assertTrue("Home reserves a gap below the greeting", greeting.bottom < avatar.top)
                // Semantics include transparent artwork and bubble padding; inspect the captured pixels for clearance.
                compose.onNodeWithTag("home-companion-message").assertIsDisplayed()
                val bitmap = compose.onNodeWithTag("luna-home-review").captureToImage().asAndroidBitmap()
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                val directory = File(context.filesDir, "test-screenshots/luna-hungry-home").apply { mkdirs() }
                File(directory, "$name-font-${scale.toInt()}.png").outputStream().use {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                bitmap.recycle()
            }
        }
    }
}
