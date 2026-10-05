package com.monsters.mobimon.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.monsters.mobimon.core.ui.resolveCompanionBackground
import com.monsters.mobimon.testing.ComposeTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = ComposeTestApplication::class, qualifiers = "ko-rKR-w2560dp-h1184dp-mdpi")
class StartupLoadingTest {
    @get:Rule val compose = createComposeRule()

    private fun state(
        appearance: Boolean = true,
        home: Boolean = false,
    ) = StartupContent(
        appearanceResolved = appearance,
        homeResolved = home,
        friendId = "friend:mobi",
        background = resolveCompanionBackground("Sunset", "background:cyberpunk_city").layer,
    )

    @Test
    fun starsThenEquippedSceneThenFaceAndActualReadinessReleaseHome() {
        val current = mutableStateOf(state(appearance = false))
        compose.mainClock.autoAdvance = false
        compose.setContent { StartupLoadingHost(current.value) { Text("Home ready") } }
        compose.mainClock.advanceTimeBy(2400)
        compose.onNodeWithTag("startup-scene").assertDoesNotExist()
        compose.onNodeWithText("모비를 만나고 있어요").assertDoesNotExist()
        compose.runOnIdle { current.value = state() }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("startup-scene").assertExists()
        compose.onNodeWithText("모비를 만나고 있어요").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("모비를 만나고 있어요").assertIsDisplayed()
        compose.onNodeWithText("Home ready").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("모비를 만나고 있어요").assertIsDisplayed()
        compose.runOnIdle { current.value = state(home = true) }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(450)
        compose.onNodeWithText("Home ready").assertIsDisplayed()
        compose.onNodeWithTag("startup-loading").assertDoesNotExist()
    }

    @Test
    fun nativeSplashHandoffReleasesTheAnimationClock() {
        val released = mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            StartupLoadingHost(state(home = true), launchReady = released.value) { Text("Home ready") }
        }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("startup-scene").assertDoesNotExist()
        compose.onNodeWithText("Home ready").assertDoesNotExist()
        compose.runOnIdle { released.value = true }
        compose.mainClock.advanceTimeBy(3500)
        compose.onNodeWithText("Home ready").assertIsDisplayed()
    }

    @Test
    fun launchesWithoutNativeSplashCallbacksStillReachHome() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            StartupLoadingHost(state(home = true), launchReady = false) { Text("Home ready") }
        }
        compose.mainClock.advanceTimeBy(4200)
        compose.onNodeWithText("Home ready").assertIsDisplayed()
    }

    @Test
    fun restoredCompletedShellDoesNotReplayStartup() {
        val restoration = StateRestorationTester(compose)
        compose.mainClock.autoAdvance = false
        restoration.setContent { StartupLoadingHost(state(home = true)) { Text("Home ready") } }
        compose.mainClock.advanceTimeBy(3500)
        compose.onNodeWithText("Home ready").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Home ready").assertIsDisplayed()
        compose.onNodeWithTag("startup-loading").assertDoesNotExist()
    }

    @Test
    fun slowStorageCanBeDismissedWithoutLateCallbackReopeningLoading() {
        val current = mutableStateOf(state(appearance = false))
        compose.mainClock.autoAdvance = false
        compose.setContent { StartupLoadingHost(current.value) { Text("Home ready") } }
        compose.mainClock.advanceTimeBy(8500)
        compose.onNodeWithText("홈으로 이동").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Home ready").assertIsDisplayed()
        compose.runOnIdle { current.value = state(home = true) }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(3500)
        compose.onNodeWithTag("startup-loading").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "ko-rKR-w1024dp-h600dp-mdpi")
    fun reducedMotionShowsSelectedFaceWithoutIntroDelayAtLargeFontScale() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                StartupLoadingHost(state().copy(friendId = "friend:luna"), reducedMotion = true) { Text("Home ready") }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("루나를 만나고 있어요").assertIsDisplayed()
        compose.onNodeWithTag("startup-face").assertIsDisplayed()
        compose.onNodeWithText("Home ready").assertDoesNotExist()
    }
}
