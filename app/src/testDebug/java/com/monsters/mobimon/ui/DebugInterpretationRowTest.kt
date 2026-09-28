package com.monsters.mobimon.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DebugInterpretationRowTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun formulaButtonShowsCalculationDetails() {
        compose.setContent {
            DebugInterpretationRow(
                label = "isRaining",
                value = "false",
                inputType = DebugInterpretationInputType.Boolean,
                formula = "Vehicle.Body.Raindetection.Intensity > 0",
                onManualValueChange = {},
                onClearManualValue = {},
            )
        }

        compose.onNodeWithContentDescription("isRaining 계산식 보기").performClick()

        compose
            .onNodeWithText("Vehicle.Body.Raindetection.Intensity > 0")
            .assertIsDisplayed()
    }

    @Test
    fun booleanInterpretationUsesToggleInsteadOfTextInput() {
        var manualValue by mutableStateOf("")
        compose.setContent {
            DebugInterpretationRow(
                label = "isMoving",
                value = "false",
                inputType = DebugInterpretationInputType.Boolean,
                formula = "Vehicle.IsMoving || Vehicle.Speed > 0",
                manualValue = manualValue,
                onManualValueChange = { manualValue = it },
                onClearManualValue = { manualValue = "" },
            )
        }

        compose
            .onNodeWithTag("debug-interpretation-input-isMoving")
            .assertDoesNotExist()
        compose
            .onNodeWithTag("debug-interpretation-toggle-isMoving")
            .assertIsDisplayed()
            .assertIsOff()

        compose.onNodeWithTag("debug-interpretation-toggle-isMoving").performClick()

        compose.onNodeWithTag("debug-interpretation-toggle-isMoving").assertIsOn()
    }

    @Test
    fun textInterpretationKeepsSingleLineInput() {
        compose.setContent {
            DebugInterpretationRow(
                label = "gear",
                value = "P",
                inputType = DebugInterpretationInputType.Text,
                formula = "SelectedGear: 126=P, 127=D, 0=N, negative=R",
                onManualValueChange = {},
                onClearManualValue = {},
            )
        }

        compose
            .onNodeWithTag("debug-interpretation-input-gear")
            .assertIsDisplayed()
    }

    @Test
    fun presetsRenderQuickButtonsAndApplyValues() {
        var manualValue by mutableStateOf("")
        compose.setContent {
            DebugInterpretationRow(
                label = "timeOfDay",
                value = "Morning",
                formula = "hour test",
                manualValue = manualValue,
                onManualValueChange = { manualValue = it },
                onClearManualValue = { manualValue = "" },
                presets =
                    listOf(
                        "Morning (09시)" to "09:00",
                        "Day (14시)" to "14:00",
                        "Night (20시)" to "20:00",
                    ),
            )
        }

        compose.onNodeWithTag("debug-interpretation-preset-timeOfDay-14:00").assertIsDisplayed().performClick()
        org.junit.Assert.assertEquals("14:00", manualValue)
    }

    @Test
    fun narrowPreviewCanScrollToLastPreset() {
        compose.setContent {
            Box(Modifier.width(400.dp)) {
                DebugInterpretationRow(
                    label = "timeOfDay",
                    value = "Auto",
                    formula = "Local hour",
                    onManualValueChange = {},
                    onClearManualValue = {},
                    presets =
                        listOf(
                            "Midnight (01시)" to "01:00",
                            "Sunrise (06시)" to "06:00",
                            "Morning (09시)" to "09:00",
                            "Day (14시)" to "14:00",
                            "Afternoon (16시)" to "16:00",
                            "Sunset (18시)" to "18:00",
                            "Night (20시)" to "20:00",
                        ),
                )
            }
        }

        compose.onNodeWithTag("debug-interpretation-preset-timeOfDay-20:00").performScrollTo().assertIsDisplayed()
    }
}
