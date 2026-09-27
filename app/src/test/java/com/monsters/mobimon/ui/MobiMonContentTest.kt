package com.monsters.mobimon.ui

import android.app.Application
import android.graphics.Insets
import android.view.View
import android.view.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.monsters.mobimon.core.domain.AppUseState
import com.monsters.mobimon.core.navigation.AiRoute
import com.monsters.mobimon.core.navigation.AppRoute
import com.monsters.mobimon.core.navigation.CompanionRoute
import com.monsters.mobimon.core.navigation.FeatureEntry
import com.monsters.mobimon.core.navigation.FeatureNavigator
import com.monsters.mobimon.core.navigation.LocalDebugSettingsAvailable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "ko-rKR-w2560dp-h1184dp-mdpi")
class MobiMonContentTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var rootView: View
    private val mountedRoutes = mutableStateListOf<AppRoute>()

    private fun clickMenuItem(text: String) {
        compose.onNodeWithText(text).performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun menuNavigationClosesOverlayAndBackReturnsHome() {
        show()
        compose.onNodeWithText("Open menu").performClick()
        clickMenuItem("퀘스트")
        compose.onNodeWithContentDescription("닫기").assertDoesNotExist()
        compose.onNodeWithText("Route QUESTS").assertExists()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Route HOME").assertExists()
    }

    @Test
    fun notificationCardClosesMenuAndNavigatesToQuest() {
        show(notificationItems = listOf(NotificationItem("first", "첫 퀘스트 완료", NotificationKind.QUEST)))
        compose.onNodeWithText("Open menu").performClick()
        compose.onNodeWithContentDescription("알림 1건 열기").performClick()
        compose.onNodeWithTag("notification-quest-first").performClick()
        compose.onNodeWithTag("notification-popup").assertDoesNotExist()
        compose.onNodeWithText("Route QUESTS").assertExists()
    }

    @Test
    fun notificationPopupReturnsFocusToBell() {
        show()
        compose.onNodeWithText("Open menu").performClick()
        compose.onNodeWithText("홈").assertIsFocused()
        compose.onNodeWithContentDescription("알림 열기").performClick()
        compose.onNodeWithTag("notification-close").assertIsFocused()
        compose.onNodeWithContentDescription("알림 닫기").performClick()
        compose.onNodeWithTag("menu-notifications").assertIsFocused()
    }

    @Test
    fun menuRoutesConversationAndDismissesFromBackdrop() {
        show()
        compose.onNodeWithText("Open menu").performClick()
        compose.onNodeWithTag("menu-backdrop").performTouchInput {
            click(Offset(width - 24f, height / 2f))
        }
        compose.onNodeWithTag("companion-menu").assertDoesNotExist()
        compose.onNodeWithText("Open menu").performClick()
        clickMenuItem("대화하기")
        compose.onNodeWithText("Route COPILOT").assertExists()
    }

    @Test
    fun chatRoutesFromHomeAndSettingsTrackAccountWithoutAConnectionLoop() {
        val authenticated = mutableStateOf(false)
        compose.setContent {
            MobiMonContent(entries, appUseState = AppUseState.ALLOWED, conversationAuthenticated = authenticated.value)
        }
        compose.onNodeWithText("Chat").performClick()
        compose.onNodeWithText("Route COPILOT").assertExists()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Open menu").performClick()
        clickMenuItem("설정")
        compose.onNodeWithText("Chat").performClick()
        compose.onNodeWithText("Route COPILOT").assertExists()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Route SETTINGS").assertExists()
        compose.runOnIdle { authenticated.value = true }
        compose.onNodeWithText("Open menu").performClick()
        clickMenuItem("대화하기")
        compose.onNodeWithText("Route CONVERSATION").assertExists()
        compose.runOnIdle { authenticated.value = false }
        compose.onNodeWithText("Route CONVERSATION").assertDoesNotExist()
        compose.onNodeWithText("Route COPILOT").assertExists()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Route HOME").assertExists()
    }

    @Test
    fun startChatUsesLatestAuthenticationBeforeShellCollectsSession() {
        var authenticatedNow = false
        compose.setContent {
            MobiMonContent(
                entries,
                appUseState = AppUseState.ALLOWED,
                conversationAuthenticated = false,
                currentConversationAuthentication = { authenticatedNow },
            )
        }
        compose.onNodeWithText("Open menu").performClick()
        clickMenuItem("설정")
        compose.onNodeWithText("Connect").performClick()
        compose.onNodeWithText("Route COPILOT").assertExists()

        compose.runOnIdle { authenticatedNow = true }
        compose.onNodeWithText("Chat").performClick()

        compose.onNodeWithText("Route CONVERSATION").assertExists()
    }

    @Test
    fun startChatDuringRestorationDoesNotFlashConversation() {
        var startReady = false
        compose.setContent {
            MobiMonContent(
                entries,
                appUseState = AppUseState.ALLOWED,
                conversationAuthenticated = true,
                currentConversationAuthentication = { true },
                currentConversationStartReady = { startReady },
            )
        }
        compose.onNodeWithText("Open menu").performClick()
        clickMenuItem("설정")
        compose.onNodeWithText("Open menu").performClick()
        clickMenuItem("대화하기")
        compose.onNodeWithText("Route COPILOT").assertExists()
        compose.onNodeWithText("Route CONVERSATION").assertDoesNotExist()
        compose.runOnIdle { startReady = true }
        compose.onNodeWithText("Chat").performClick()
        compose.onNodeWithText("Route CONVERSATION").assertExists()
    }

    @Test
    fun menuRoutesVehicleAndCustomizationAndClosesWithBackAndClose() {
        show()
        compose.onNodeWithText("Open menu").performClick()
        compose.onNodeWithContentDescription("닫기").performClick()
        compose.onNodeWithTag("companion-menu").assertDoesNotExist()
        listOf("차량 상태" to "VEHICLE_INFO", "꾸미기" to "APPEARANCE").forEach { (label, route) ->
            compose.onNodeWithText("Open menu").performClick()
            clickMenuItem(label)
            compose.onNodeWithText("Route $route").assertExists()
            compose.onNodeWithText("Back").performClick()
        }
        compose.onNodeWithText("Open menu").performClick()
        clickMenuItem("홈")
        compose.onNodeWithText("Route HOME").assertExists()
    }

    @Test
    fun menuProfileTracksEquippedFriend() {
        val friend = mutableStateOf("friend:mobi")
        compose.setContent { MobiMonContent(entries, appUseState = AppUseState.ALLOWED, activeFriendId = friend.value) }
        compose.onNodeWithText("Open menu").performClick()
        compose.onNodeWithText("모비").assertExists()
        compose.runOnIdle { friend.value = "friend:luna" }
        compose.onNodeWithText("Luna").assertExists()
        compose.onNodeWithText("모비").assertDoesNotExist()
    }

    @Test
    fun connectionReturnsToItsSettingsOrigin() {
        show()
        compose.onNodeWithText("Open menu").performClick()
        clickMenuItem("설정")
        compose.onNodeWithText("Route SETTINGS").assertExists()
        compose.onNodeWithText("Connect").performClick()
        compose.onNodeWithText("Route COPILOT").assertExists()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Route SETTINGS").assertExists()
    }

    @Test
    fun destinationChangeKeepsOutgoingFrameUntilIncomingScreenArrives() {
        show()
        compose.mainClock.autoAdvance = false

        compose.onNodeWithText("Connect").performClick()
        compose.mainClock.advanceTimeBy(80)

        compose.runOnIdle {
            assertTrue(mountedRoutes.containsAll(listOf(CompanionRoute.HOME, AiRoute.COPILOT)))
        }
        compose.onNodeWithText("Route HOME").assertDoesNotExist()
        compose.onNodeWithText("Route COPILOT").assertExists()

        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle {
            assertTrue(
                "mounted: ${mountedRoutes.toList()}",
                mountedRoutes.toList() == listOf(AiRoute.COPILOT),
            )
        }
    }

    @Test
    fun restrictedAppUseKeepsDestinationVisibleAcrossRecovery() {
        val appUse = mutableStateOf(AppUseState.ALLOWED)
        compose.setContent { MobiMonContent(entries, appUseState = appUse.value) }
        compose.onNodeWithText("Open menu").performClick()
        clickMenuItem("퀘스트")
        compose.runOnIdle { appUse.value = AppUseState.RESTRICTED }
        compose.onNodeWithText("Route QUESTS").assertExists()
        compose.onNodeWithText("지금은 MobiMon 사용이 제한돼요").assertDoesNotExist()
        compose.runOnIdle { appUse.value = AppUseState.ALLOWED }
        compose.onNodeWithText("Route QUESTS").assertExists()
    }

    @Test
    fun restrictionGateRemovesDebugControlsBeforeTheyCanReceiveInput() {
        val appUse = mutableStateOf(AppUseState.ALLOWED)
        var debugMutations = 0
        compose.setContent {
            MobiMonContent(
                entries = entries,
                appUseState = appUse.value,
                debugOverlay = {
                    TextButton(onClick = { debugMutations++ }) { Text("Debug mutation") }
                },
            )
        }
        compose.onNodeWithText("Debug mutation").performClick()
        assertTrue(debugMutations == 1)

        compose.runOnIdle { appUse.value = AppUseState.RESTRICTED }

        compose.onNodeWithText("Debug mutation").assertDoesNotExist()
        assertTrue(debugMutations == 1)
    }

    @Test
    fun missingAppUseEvidenceKeepsReadOnlyShellVisible() {
        compose.setContent { MobiMonContent(entries) }
        compose.onNodeWithText("Route HOME").assertExists()
        compose.onNodeWithText("지금은 MobiMon 사용이 제한돼요").assertDoesNotExist()
    }

    @Test
    fun restrictedAppUseKeepsNavigationAvailable() {
        compose.setContent { MobiMonContent(entries, appUseState = AppUseState.RESTRICTED) }
        compose.onNodeWithText("Route HOME").assertExists()
        compose.onNodeWithText("Open menu").performClick()
        clickMenuItem("퀘스트")
        compose.onNodeWithText("Route QUESTS").assertExists()
        compose.onNodeWithText("지금은 MobiMon 사용이 제한돼요").assertDoesNotExist()
    }

    @Test
    fun releaseMenuVersionTapsUnlockDebuggerSettings() {
        show(debugSettingsAvailableByDefault = false)
        compose.onNodeWithText("Open menu").performClick()
        clickMenuItem("설정")
        compose.onNodeWithText("Debugger").assertDoesNotExist()

        compose.onNodeWithText("Open menu").performClick()
        repeat(10) {
            compose.onNodeWithTag("menu-version").performScrollTo().performClick()
        }
        clickMenuItem("설정")

        compose.onNodeWithText("Route SETTINGS").assertExists()
        compose.onNodeWithText("Debugger").assertExists()
    }

    @Test
    fun releaseMenuVersionUnlockResetsDebuggerModeToDisabled() {
        var resetRequests = 0
        show(
            debugSettingsAvailableByDefault = false,
            onReleaseDebuggerUnlocked = { resetRequests++ },
        )
        compose.onNodeWithText("Open menu").performClick()

        repeat(9) {
            compose.onNodeWithTag("menu-version").performScrollTo().performClick()
        }
        compose.runOnIdle { assertTrue(resetRequests == 0) }

        compose.onNodeWithTag("menu-version").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(resetRequests == 1) }

        compose.onNodeWithTag("menu-version").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(resetRequests == 1) }
    }

    @Test
    fun restrictedAppUseDoesNotUnlockDebuggerFromVersionTaps() {
        var resetRequests = 0
        show(
            appUseState = AppUseState.RESTRICTED,
            debugSettingsAvailableByDefault = false,
            onReleaseDebuggerUnlocked = { resetRequests++ },
        )
        compose.onNodeWithText("Open menu").performClick()

        repeat(10) {
            compose.onNodeWithTag("menu-version").performScrollTo().performClick()
        }
        compose.runOnIdle { assertTrue(resetRequests == 0) }
        compose.onNodeWithText("debugger 버튼이 활성화 되었습니다").assertDoesNotExist()
        compose.onNodeWithContentDescription("닫기").performClick()
        compose.onNodeWithText("Open menu").performClick()
        clickMenuItem("설정")

        compose.onNodeWithText("Debugger").assertDoesNotExist()
    }

    @Test
    fun releaseMenuVersionTapsShowUnlockToastCountdown() {
        show(debugSettingsAvailableByDefault = false)
        compose.onNodeWithText("Open menu").performClick()

        repeat(4) {
            compose.onNodeWithTag("menu-version").performScrollTo().performClick()
        }
        compose.onNodeWithText("debugger 버튼 활성화까지 5회 남았습니다").assertDoesNotExist()

        listOf(
            "debugger 버튼 활성화까지 5회 남았습니다",
            "debugger 버튼 활성화까지 4회 남았습니다",
            "debugger 버튼 활성화까지 3회 남았습니다",
            "debugger 버튼 활성화까지 2회 남았습니다",
            "debugger 버튼 활성화까지 1회 남았습니다",
            "debugger 버튼이 활성화 되었습니다",
        ).forEach { message ->
            compose.onNodeWithTag("menu-version").performScrollTo().performClick()
            compose.onNodeWithText(message).assertExists()
        }
    }

    @Test
    fun releaseMenuVersionTapCountResetsAfterThreeSeconds() {
        show(debugSettingsAvailableByDefault = false, reducedMotion = true)
        compose.onNodeWithText("Open menu").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("menu-version").assertExists()

        repeat(5) {
            compose.onNodeWithTag("menu-version").performScrollTo().performClick()
        }
        compose.onNodeWithText("debugger 버튼 활성화까지 5회 남았습니다").assertExists()

        compose.mainClock.advanceTimeBy(3_100)
        compose.waitForIdle()

        repeat(5) {
            compose.onNodeWithTag("menu-version").performScrollTo().performClick()
        }
        compose.onNodeWithText("debugger 버튼 활성화까지 5회 남았습니다").assertExists()

        clickMenuItem("설정")
        compose.onNodeWithText("Debugger").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "ko-rKR-w1792dp-h829dp")
    fun menuFitsItsWindowAndKeepsAccessibleTargets() {
        show()
        compose.onNodeWithText("Open menu").performClick()
        val host = compose.onNodeWithTag("menu-host").fetchSemanticsNode().boundsInRoot
        val panel = compose.onNodeWithTag("companion-menu").fetchSemanticsNode().boundsInRoot
        assertTrue(panel.width <= host.width)
        assertTrue(panel.height <= host.height)
        compose.onNodeWithContentDescription("닫기").assertWidthIsAtLeast(76.dp).assertHeightIsAtLeast(76.dp)
        listOf("홈", "대화하기", "퀘스트", "차량 상태", "꾸미기", "설정").forEach { label ->
            compose.onNodeWithText(label).assertHeightIsAtLeast(76.dp).assertWidthIsAtLeast(76.dp)
        }
        compose.onNodeWithText("홈").assertIsFocused()
    }

    @Test
    @Config(qualifiers = "ko-rKR-w2560dp-h1440dp-mdpi")
    fun liveSystemBarInsetsResizeTheDestinationAndMenu() {
        show(debugSettingsAvailableByDefault = false)

        fun bars(
            top: Int,
            bottom: Int,
        ) {
            compose.runOnIdle {
                rootView.dispatchApplyWindowInsets(
                    WindowInsets
                        .Builder()
                        .setInsets(WindowInsets.Type.statusBars(), Insets.of(0, top, 0, 0))
                        .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, bottom))
                        .setVisible(WindowInsets.Type.systemBars(), true)
                        .build(),
                )
            }
            compose.waitForIdle()
        }
        bars(76, 96)
        val before = compose.onNodeWithTag("test-destination").fetchSemanticsNode().boundsInRoot
        assertEquals(1268f, before.height, 1f)
        bars(96, 160)
        val after = compose.onNodeWithTag("test-destination").fetchSemanticsNode().boundsInRoot
        assertEquals(96f, after.top, 1f)
        assertEquals(1184f, after.height, 1f)
        compose.onNodeWithText("Open menu").performClick()
        val host = compose.onNodeWithTag("menu-host").fetchSemanticsNode().boundsInRoot
        val panel = compose.onNodeWithTag("companion-menu").fetchSemanticsNode().boundsInRoot
        assertEquals(1184f, host.height, 1f)
        assertEquals(host.height, panel.height, 1f)
        repeat(5) {
            compose.onNodeWithTag("menu-version").performClick()
        }
        val toast = compose.onNodeWithText("debugger 버튼 활성화까지 5회 남았습니다").fetchSemanticsNode().boundsInRoot
        assertTrue("Debugger notice must clear the navigation bar", toast.bottom <= after.bottom - 32f)
        compose.onNodeWithContentDescription("닫기").performClick()
        bars(76, 96)
        assertEquals(before, compose.onNodeWithTag("test-destination").fetchSemanticsNode().boundsInRoot)
    }

    private fun show(
        appUseState: AppUseState = AppUseState.ALLOWED,
        debugSettingsAvailableByDefault: Boolean = true,
        reducedMotion: Boolean = false,
        onReleaseDebuggerUnlocked: () -> Unit = {},
        notificationItems: List<NotificationItem> = emptyList(),
    ) {
        compose.setContent {
            MobiMonContent(
                entries,
                notificationItems = notificationItems,
                appUseState = appUseState,
                reducedMotion = reducedMotion,
                debugSettingsAvailableByDefault = debugSettingsAvailableByDefault,
                onReleaseDebuggerUnlocked = onReleaseDebuggerUnlocked,
            )
        }
    }

    private val entries =
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
                        mountedRoutes.add(route)
                        onDispose { mountedRoutes.remove(route) }
                    }
                    val view = LocalView.current
                    SideEffect { rootView = view.parent as View }
                    Column(modifier.fillMaxSize().testTag("test-destination")) {
                        Text("Route ${route.name}")
                        if (route == CompanionRoute.SETTINGS && LocalDebugSettingsAvailable.current) {
                            Text("Debugger")
                        }
                        TextButton(onClick = navigator.openMenu) { Text("Open menu") }
                        TextButton(onClick = { navigator.navigate(AiRoute.COPILOT) }) { Text("Connect") }
                        TextButton(onClick = { navigator.navigate(AiRoute.CONVERSATION) }) { Text("Chat") }
                        TextButton(onClick = navigator.back) { Text("Back") }
                    }
                }
            },
        )
}
