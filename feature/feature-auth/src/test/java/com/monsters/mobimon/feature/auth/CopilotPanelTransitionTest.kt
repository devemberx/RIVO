package com.monsters.mobimon.feature.auth

import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.monsters.mobimon.core.ui.MobiMonTheme
import com.monsters.mobimon.feature.auth.testing.ComposeTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = ComposeTestApplication::class)
class CopilotPanelTransitionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun outgoingContentCannotDispatchAndCountdownDoesNotRestartThePanel() {
        var state: CopilotUiState by mutableStateOf(CopilotUiState.Introduction())
        var staleAction: ((CopilotAction) -> Unit)? = null
        var activeAction: ((CopilotAction) -> Unit)? = null
        var waitingMounts = 0
        var introductionMounted = false
        val actions = mutableListOf<CopilotAction>()
        compose.setContent {
            MobiMonTheme {
                CopilotPanelTransition(state, actions::add, interactionAllowed = true) { shown, action ->
                    if (shown is CopilotUiState.Introduction) {
                        DisposableEffect(Unit) {
                            introductionMounted = true
                            onDispose { introductionMounted = false }
                        }
                        SideEffect { staleAction = action }
                        Text("Introduction")
                    } else {
                        DisposableEffect(Unit) {
                            waitingMounts++
                            onDispose {}
                        }
                        SideEffect { activeAction = action }
                        Text("Waiting")
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            state = CopilotUiState.Waiting("TEST", 272)
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithText("Introduction").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(introductionMounted)
            staleAction!!(CopilotAction.REQUEST_CODE)
            activeAction!!(CopilotAction.RECHECK)
            assertEquals(listOf(CopilotAction.RECHECK), actions)
        }
        compose.mainClock.advanceTimeBy(250)
        compose.runOnIdle { assertFalse(introductionMounted) }
        compose.runOnIdle {
            state = CopilotUiState.Waiting("TEST", 271)
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle { assertEquals(1, waitingMounts) }
    }

    @Test
    fun expiryAndAccessChecksRemoveOutgoingContentImmediately() {
        var state: CopilotUiState by mutableStateOf(CopilotUiState.Waiting("OLD CODE", 1))
        val mounted = mutableSetOf<CopilotUiState>()
        compose.setContent {
            MobiMonTheme {
                CopilotPanelTransition(
                    state,
                    {},
                    interactionAllowed = true,
                ) { shown, _ ->
                    DisposableEffect(shown) {
                        mounted.add(shown)
                        onDispose { mounted.remove(shown) }
                    }
                    Text(shown.toString())
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            state = CopilotUiState.Expired
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle { assertEquals(setOf(CopilotUiState.Expired), mounted) }
        compose.runOnIdle {
            state = CopilotUiState.Introduction()
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle {
            state = CopilotUiState.AccessCheck("@example", CopilotAccessIssue.PERMISSION)
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle { assertTrue(mounted.single() is CopilotUiState.AccessCheck) }
    }
}
