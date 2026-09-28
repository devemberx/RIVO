package com.monsters.mobimon.vehicle

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monsters.mobimon.core.domain.DrivingState
import com.monsters.mobimon.core.domain.SignalQuality
import com.monsters.mobimon.core.domain.SignalSource
import com.monsters.mobimon.core.domain.VehicleCardVssDefaults
import com.monsters.mobimon.core.domain.VehicleSnapshot
import com.monsters.mobimon.core.domain.VehicleWarning
import com.monsters.mobimon.core.domain.WarningSeverity
import com.monsters.mobimon.core.ui.LocalMobiMonMotionEnabled
import com.monsters.mobimon.core.ui.MobiMonTheme
import com.monsters.mobimon.feature.vehicle.VehicleInfoScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Native AAOS rendering of isolated display samples; no real vehicle adapter is exercised. */
@RunWith(AndroidJUnit4::class)
class VehicleLayoutDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun vehicleStatesAndCardSelectionProduceNativeReviewImages() {
        val normal = sample()
        var snapshot by mutableStateOf(normal)
        var parkingRequired by mutableStateOf(false)
        var confirmedCards: List<String>? = null
        compose.setContent {
            CompositionLocalProvider(LocalMobiMonMotionEnabled provides false) {
                MobiMonTheme {
                    VehicleInfoScreen(
                        snapshot = snapshot,
                        parkingRequired = parkingRequired,
                        onCardSelectionConfirmed = { confirmedCards = it },
                        onHome = {},
                    )
                }
            }
        }
        capture("checked-items")
        compose.runOnIdle { snapshot = normal.copy(batteryPercent = 18) }
        capture("charging-required")
        compose.runOnIdle {
            snapshot =
                normal.copy(
                    batteryPercent = 65,
                    tirePressureStatus = "NG",
                    warnings =
                        listOf(
                            VehicleWarning(
                                item = "타이어",
                                location = "왼쪽 앞바퀴",
                                severity = WarningSeverity.CAUTION,
                                description = "앞왼쪽 타이어를 확인해 주세요.",
                                nextAction = "타이어 공기압을 점검해 주세요.",
                                observedAtMillis = 10_000,
                            ),
                        ),
                )
        }
        capture("tire-warning")
        compose.runOnIdle { snapshot = normal.copy(vssCardSignals = VehicleCardVssDefaults.values) }
        compose.onNodeWithTag("vehicle-card-edit-button").performClick()
        compose.onNodeWithTag("vehicle-card-selector").assertIsDisplayed()
        capture("card-selector")
        compose.onNodeWithTag("vehicle-dialog-confirm").performClick()
        compose.onNodeWithTag("vehicle-card-selector").assertDoesNotExist()
        compose.runOnIdle { assertTrue(confirmedCards?.first() == "battery-health") }
        compose.onNodeWithTag("vehicle-card-edit-button").performClick()
        compose.runOnIdle { parkingRequired = true }
        compose.onNodeWithTag("vehicle-card-selector").assertDoesNotExist()
        capture("parking-required")
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        compose.waitForIdle()
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
        val output = File(instrumentation.targetContext.filesDir, "test-screenshots/vehicle").apply { mkdirs() }
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(output, "$name.png").outputStream().use {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        image.recycle()
    }

    private fun sample() =
        VehicleSnapshot(
            id = "vehicle-layout-review",
            epoch = "vehicle-layout-review",
            sequence = 1,
            receivedAtMillis = 10_000,
            source = SignalSource.SIMULATED,
            drivingState = DrivingState.PARKED,
            quality = SignalQuality.VALID,
            speed = 0,
            gear = "P",
            batteryPercent = 82,
            tirePressureStatus = "OK",
            outsideTemperature = 18,
            isRaining = false,
            attentionLevel = 85,
            isEmergencyBraking = false,
            isDrowsy = false,
            isDistracted = false,
            isCharging = false,
            washerFluidLevel = 68,
        )
}
