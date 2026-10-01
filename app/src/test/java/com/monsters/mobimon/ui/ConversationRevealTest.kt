package com.monsters.mobimon.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.monsters.mobimon.core.domain.AppUseState
import com.monsters.mobimon.core.navigation.AiRoute
import com.monsters.mobimon.core.navigation.AppRoute
import com.monsters.mobimon.core.navigation.CompanionRoute
import com.monsters.mobimon.core.navigation.FeatureEntry
import com.monsters.mobimon.core.navigation.FeatureNavigator
import com.monsters.mobimon.testing.ComposeTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = ComposeTestApplication::class, qualifiers = "ko-rKR-w1000dp-h700dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConversationRevealTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View
    private lateinit var backDispatcher: OnBackPressedDispatcher
    private val mounted = mutableSetOf<AppRoute>()
    private val appUse = mutableStateOf(AppUseState.ALLOWED)

    @Test fun opensFromActionAndClosesToItWithHomeStationaryBehind() {
        show()
        compose.onNodeWithText("Talk").performClick()
        compose.mainClock.advanceTimeBy(96)
        compose.onNodeWithText("Talk").assertDoesNotExist()
        compose.onNodeWithText("Back").assertExists()
        compose.runOnIdle { assertTrue(mounted.containsAll(listOf(CompanionRoute.HOME, AiRoute.COPILOT))) }
        capture("opening") { bitmap ->
            assertEquals(HOME_COLOR, bitmap.getPixel(10, 350))
            assertEquals(CONVERSATION_COLOR, bitmap.getPixel(500, 538))
        }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle { assertEquals(setOf(AiRoute.COPILOT), mounted) }
        capture("open") { assertEquals(CONVERSATION_COLOR, it.getPixel(10, 350)) }

        compose.onNodeWithText("Back").performClick()
        compose.mainClock.advanceTimeBy(96)
        compose.onNodeWithText("Back").assertDoesNotExist()
        capture("closing") { bitmap ->
            assertEquals(HOME_COLOR, bitmap.getPixel(10, 350))
            assertEquals(CONVERSATION_COLOR, bitmap.getPixel(500, 538))
        }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertEquals(setOf(CompanionRoute.HOME), mounted) }
    }

    @Test fun backDuringRevealRemovesOutgoingControlsButRestrictionKeepsDestinationVisible() {
        show()
        compose.onNodeWithText("Talk").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { backDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("Back").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithText("Talk").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { appUse.value = AppUseState.RESTRICTED }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Talk").assertDoesNotExist()
        compose.onNodeWithText("지금은 MobiMon 사용이 제한돼요").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithText("Back").assertExists()
    }

    @Test fun reducedMotionSettlesOnTheNextFrames() {
        show(reducedMotion = true)
        compose.onNodeWithText("Talk").performClick()
        compose.mainClock.advanceTimeBy(32)
        capture("reduced-motion") { assertEquals(CONVERSATION_COLOR, it.getPixel(10, 350)) }
        compose.onNodeWithText("Back").performClick()
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("Back").assertDoesNotExist()
        compose.onNodeWithText("Talk").assertExists()
    }

    @Test fun hiddenBackIgnoresTouchesUntilRevealed() {
        show()
        compose.onNodeWithText("Talk").performClick()
        compose.mainClock.advanceTimeBy(400)
        val backCenter =
            compose
                .onNodeWithText("Back")
                .fetchSemanticsNode()
                .boundsInRoot.center
        compose.onRoot().performTouchInput { click(backCenter) }
        compose.mainClock.advanceTimeBy(300)

        compose.onNodeWithText("Talk").performClick()
        compose.mainClock.advanceTimeBy(32)
        capture("hidden-back") { bitmap ->
            assertEquals(HOME_COLOR, bitmap.getPixel(backCenter.x.toInt(), backCenter.y.toInt()))
        }
        compose.onRoot().performTouchInput { click(backCenter) }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Back").assertExists()

        compose.onRoot().performTouchInput { click(backCenter) }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Talk").assertExists()
    }

    private fun show(reducedMotion: Boolean = false) {
        val entries =
            setOf(
                object : FeatureEntry {
                    override val routes = AppRoute.entries.toSet()

                    @Composable
                    override fun Content(
                        route: AppRoute,
                        navigator: FeatureNavigator,
                        modifier: Modifier,
                    ) {
                        DisposableEffect(route) {
                            mounted.add(route)
                            onDispose { mounted.remove(route) }
                        }
                        val home = route == CompanionRoute.HOME
                        Column(modifier.fillMaxSize().background(Color(if (home) HOME_COLOR else CONVERSATION_COLOR))) {
                            if (home) {
                                TextButton(
                                    onClick = { navigator.navigateFrom(AiRoute.COPILOT, Rect(400f, 500f, 600f, 576f)) },
                                ) {
                                    Text("Talk")
                                }
                            } else {
                                TextButton(onClick = navigator.back) { Text("Back") }
                            }
                        }
                    }
                },
            )
        compose.setContent {
            val currentView = LocalView.current
            val currentBackDispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            SideEffect {
                view = currentView
                backDispatcher = currentBackDispatcher
            }
            MobiMonContent(entries, appUseState = appUse.value, reducedMotion = reducedMotion, debugOverlay = {})
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun capture(
        name: String,
        verify: (Bitmap) -> Unit,
    ) {
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val directory = File("build/reports/conversation-motion").apply { mkdirs() }
                File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                verify(bitmap)
            } finally {
                bitmap.recycle()
            }
        }
    }

    private companion object {
        const val HOME_COLOR = 0xFF18375D.toInt()
        const val CONVERSATION_COLOR = 0xFF387966.toInt()
    }
}
