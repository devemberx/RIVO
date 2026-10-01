package com.monsters.mobimon.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-rKR-w2560dp-h1184dp-mdpi")
class MobiMonParkingInterruptionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun interruptionDimsTheExistingParkingBadgeInsteadOfDrawingAnotherAboveTheScrim() {
        compose.setContent {
            MobiMonTheme {
                Box(Modifier.fillMaxSize()) {
                    MobiMonParkingStatusBadge(
                        confirmed = false,
                        modifier = Modifier.align(Alignment.TopEnd).padding(end = 72.dp, top = 36.dp),
                    )
                    MobiMonParkingInterruption("title", "body", "instruction", "preserved", onHome = {})
                }
            }
        }

        compose.onAllNodesWithContentDescription("주차 후 이용").assertCountEquals(1)
    }

    @Test
    fun parkingInterruptionShowsRouteCopyAndOnlyHomeAction() {
        var homeCalls = 0
        compose.setContent {
            MobiMonTheme {
                MobiMonParkingInterruption(
                    title = "주차 후 차량 상태를 확인해요",
                    body = "차량 상태 확인과 카드 변경은 주차 상태에서만 할 수 있어요.",
                    instruction = "안전한 곳에 주차한 뒤 다시 들어와 주세요.",
                    preserved = "홈으로 돌아가도 카드 설정은 유지돼요.",
                    onHome = { homeCalls++ },
                )
            }
        }

        compose.onNodeWithTag("parking-interruption-dialog").assertIsDisplayed()
        compose.onNodeWithText("주차 후 차량 상태를 확인해요").assertIsDisplayed()
        compose.onNodeWithText("차량 상태 확인과 카드 변경은 주차 상태에서만 할 수 있어요.").assertIsDisplayed()
        compose.onNodeWithText("홈으로 돌아가도 카드 설정은 유지돼요.").assertIsDisplayed()
        compose.onNodeWithTag("parking-interruption-home").performClick()
        assertEquals(1, homeCalls)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun tabKeepsFocusOnTheModalHomeAction() {
        compose.setContent {
            MobiMonTheme {
                Box(Modifier.fillMaxSize()) {
                    Button(onClick = {}, modifier = Modifier.testTag("underlying-action")) { Text("behind") }
                    MobiMonParkingInterruption("title", "body", "instruction", "preserved", onHome = {})
                }
            }
        }

        compose
            .onNodeWithTag("parking-interruption-home")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()
        compose.onNodeWithTag("parking-interruption-home").performKeyInput { pressKey(Key.Tab) }
        compose.onNodeWithTag("parking-interruption-home").assertIsFocused()
    }

    @Test
    fun rememberParkingInterruptionReturnsTrueWhenUnparked() {
        var unparkedInterruption = false
        var parkedInterruption = true
        compose.setContent {
            unparkedInterruption = rememberParkingInterruption(parkedVerified = false)
            parkedInterruption = rememberParkingInterruption(parkedVerified = true)
        }

        assertEquals(true, unparkedInterruption)
        assertEquals(false, parkedInterruption)
    }
}
