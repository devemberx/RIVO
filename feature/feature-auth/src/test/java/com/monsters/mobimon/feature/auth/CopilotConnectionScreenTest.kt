package com.monsters.mobimon.feature.auth

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.monsters.mobimon.core.domain.AuthenticationProblem
import com.monsters.mobimon.core.ui.MobiMonTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-rKR-w2560dp-h1248dp-mdpi")
@OptIn(ExperimentalTestApi::class)
class CopilotConnectionScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var reviewView: View

    @Test
    @Config(qualifiers = "ko-rKR-w2560dp-h1164dp-mdpi")
    fun shorterWideWindowKeepsConnectionActionsReachable() {
        compose.setContent {
            MobiMonTheme { CopilotConnectionScreen(CopilotUiState.Introduction(), {}, interactionAllowed = true) }
        }
        compose.onNodeWithTag("copilot-reference").assertDoesNotExist()
        compose
            .onNodeWithText("QR로 연결하기")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
        compose.onNodeWithText("나중에").performScrollTo().assertIsDisplayed()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun unavailableIntroductionKeepsReferenceCompositionAndDisablesQr() {
        var unavailable by mutableStateOf(false)
        lateinit var view: View
        compose.setContent {
            val currentView = LocalView.current
            SideEffect { view = currentView }
            MobiMonTheme {
                CopilotConnectionScreen(
                    CopilotUiState.Introduction(connectionUnavailable = unavailable),
                    {},
                    interactionAllowed = true,
                )
            }
        }
        val availableBounds = compose.onNodeWithTag("copilot-panel").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("copilot-reference").assertExists()
        compose.runOnIdle { unavailable = true }
        compose.onNodeWithTag("copilot-reference").assertExists()
        assertEquals(availableBounds, compose.onNodeWithTag("copilot-panel").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("아직 계정 연결을 이용할 수 없어요.").assertExists()
        compose.onNodeWithText("QR로 연결하기").assertIsNotEnabled()
        compose.onNodeWithText("나중에").assertIsEnabled()
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File("build/reports/copilot-ui").apply { mkdirs() }
            File(directory, "P51-unavailable.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            bitmap.recycle()
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun unavailableIntroductionKeepsStepsAndDisabledQr() {
        lateinit var view: View
        compose.setContent {
            val currentView = LocalView.current
            SideEffect { view = currentView }
            MobiMonTheme {
                CopilotConnectionScreen(
                    CopilotUiState.Introduction(connectionUnavailable = true),
                    {},
                    interactionAllowed = true,
                )
            }
        }
        compose.onNodeWithTag("copilot-reference").assertExists()
        compose.onNodeWithText("계정 연결").assertExists()
        compose.onNodeWithText("아직 계정 연결을 이용할 수 없어요.", substring = true).assertExists()
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File("build/reports/copilot-ui").apply { mkdirs() }
            File(directory, "P51-unavailable-target.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            bitmap.recycle()
        }
        compose.onNodeWithText("QR로 연결하기").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun introductionExplainsSharingAndRequestsConnectionWithoutInventingAnAccount() {
        val actions = mutableListOf<CopilotAction>()
        show(CopilotUiState.Introduction(), actions::add)
        compose.onNodeWithText("인증 토큰은 이 기기에 암호화해 저장해요.", substring = true).assertExists()
        click("QR로 연결하기")
        assertEquals(listOf(CopilotAction.REQUEST_CODE), actions)
        compose.onNodeWithText("연결이 완료됐어요.").assertDoesNotExist()
        click("나중에")
        assertEquals(CopilotAction.CANCEL, actions.last())
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun unknownParkingBlocksConnectionButKeepsExitAvailable() {
        val actions = mutableListOf<CopilotAction>()
        show(CopilotUiState.Introduction(), actions::add, allowed = false)
        val badge =
            compose
                .onNodeWithContentDescription(
                    "주차 후 이용",
                ).assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        assertEquals(36f, badge.top, 1f)
        assertEquals(2488f, badge.right, 1f)
        compose
            .onNodeWithText("QR로 연결하기")
            .assertIsDisplayed()
            .assertIsNotEnabled()
            .performClick()
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(reviewView.width, reviewView.height, Bitmap.Config.ARGB_8888)
            reviewView.draw(Canvas(bitmap))
            val directory = File("build/reports/copilot-ui").apply { mkdirs() }
            File(directory, "introduction-parking-unverified.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            bitmap.recycle()
        }
        compose.onNodeWithContentDescription("뒤로").assertIsDisplayed().performClick()
        assertEquals(listOf(CopilotAction.BACK), actions)
    }

    @Test
    fun waitingOffersQrHelpAndManualRecheckWithoutClaimingApproval() {
        val actions = mutableListOf<CopilotAction>()
        show(CopilotUiState.Waiting("TEST · CODE", 272), actions::add)
        compose.onNodeWithContentDescription("GitHub 계정 연결 QR 코드").assertExists()
        compose.onNodeWithText("04:32").assertExists()
        compose.onNodeWithText("TEST · CODE").assertExists()
        click("QR 스캔이 어려워요")
        click("승인했어요")
        compose.onNodeWithContentDescription("계정 연결 닫기").assertIsDisplayed().performClick()
        assertEquals(listOf(CopilotAction.SHOW_ADDRESS, CopilotAction.RECHECK, CopilotAction.CANCEL), actions)
        compose.onNodeWithText("연결이 완료됐어요.").assertDoesNotExist()
    }

    @Test
    fun missingQrFallsBackToReadableAddressAndCode() {
        show(CopilotUiState.Waiting("TEST · CODE", 120), qr = false)
        compose.onNodeWithText("github.com/login/device").assertExists()
        compose.onNodeWithText("TEST · CODE").assertExists()
        compose.onNodeWithContentDescription("GitHub 계정 연결 QR 코드").assertDoesNotExist()
    }

    @Test
    fun addressHelpRetainsApprovalDataAndReturnsToQr() {
        val actions = mutableListOf<CopilotAction>()
        show(CopilotUiState.Waiting("TEST · CODE", 272, showAddress = true), actions::add)
        compose.onNodeWithText("github.com/login/device").assertExists()
        click("QR로 돌아가기")
        assertEquals(listOf(CopilotAction.SHOW_QR), actions)
    }

    @Test
    fun checkingDisablesRepeatedRechecksAndExpiryRemovesTheOldCode() {
        var state: CopilotUiState by mutableStateOf(CopilotUiState.Waiting("OLD · CODE", 1, checking = true))
        val actions = mutableListOf<CopilotAction>()
        compose.setContent { MobiMonTheme { CopilotConnectionScreen(state, actions::add, interactionAllowed = true) } }
        compose
            .onNodeWithText("승인했어요")
            .assertIsDisplayed()
            .assertIsNotEnabled()
            .performClick()
        assertTrue(actions.isEmpty())
        compose.runOnIdle { state = CopilotUiState.Waiting("OLD · CODE", 0) }
        compose.onNodeWithText("OLD · CODE").assertDoesNotExist()
        compose.onNodeWithText("승인했어요").assertDoesNotExist()
        click("새 QR 받기")
        assertEquals(listOf(CopilotAction.REQUEST_CODE), actions)
    }

    @Test
    fun accessProblemsKeepTheApprovedIdentityAndReportSpecificReasons() {
        var state by mutableStateOf(CopilotUiState.AccessCheck("@test-user", CopilotAccessIssue.PERMISSION))
        compose.setContent { MobiMonTheme { CopilotConnectionScreen(state, {}, interactionAllowed = true) } }
        val messages =
            mapOf(
                CopilotAccessIssue.PERMISSION to "이 계정의 Copilot 이용 권한을 확인해 주세요.",
                CopilotAccessIssue.SUBSCRIPTION to "이 계정의 Copilot 요금제를 확인해 주세요.",
                CopilotAccessIssue.USAGE_LIMIT to "Copilot 사용량 한도에 도달했어요.",
                CopilotAccessIssue.SERVICE_UNAVAILABLE to "지금은 Copilot 서비스를 확인할 수 없어요.",
            )
        messages.forEach { (issue, message) ->
            compose.runOnIdle { state = state.copy(reason = issue) }
            compose.onNodeWithText(message).assertExists()
            compose.onNodeWithText("@test-user").assertExists()
            compose.onNodeWithText("연결이 완료됐어요.").assertDoesNotExist()
        }
    }

    @Test
    fun networkFailureOffersRecheckWithoutClearingConnectionInformation() {
        val actions = mutableListOf<CopilotAction>()
        show(CopilotUiState.AuthenticationStatus(problem = AuthenticationProblem.NETWORK), actions::add)

        compose.onNodeWithText("계정 연결").assertDoesNotExist()
        compose.onNodeWithText("이용 확인").assertDoesNotExist()
        compose.onNodeWithText("대화 시작").assertDoesNotExist()
        val panel = compose.onNodeWithTag("copilot-panel").fetchSemanticsNode().boundsInRoot
        val heading =
            compose
                .onNodeWithText("GitHub 계정 인증")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        assertEquals(panel.left + 64f, heading.left, 1f)
        assertEquals(panel.top + 64f, heading.top, 1f)
        compose.onNodeWithText("네트워크 연결을 확인해 주세요. 저장된 연결 정보는 유지돼요.").assertIsDisplayed()
        compose.onNodeWithText("연결 정보 지우기").assertDoesNotExist()
        click("다시 확인")
        assertEquals(listOf(CopilotAction.RECHECK), actions)
    }

    @Test
    fun storageFailureStillOffersLocalConnectionClearing() {
        val actions = mutableListOf<CopilotAction>()
        show(CopilotUiState.AuthenticationStatus(problem = AuthenticationProblem.STORAGE), actions::add)

        compose.onNodeWithText("계정 연결").assertIsDisplayed()
        click("연결 정보 지우기")
        assertEquals(listOf(CopilotAction.DISCONNECT), actions)
    }

    @Test
    fun connectedAccountStartsOnlyOnUserActionAndCanReturnToSettings() {
        val actions = mutableListOf<CopilotAction>()
        show(CopilotUiState.Connected("@test-user"), actions::add)
        assertTrue(actions.isEmpty())
        compose.onNodeWithText("@test-user").assertExists()
        click("모비와 대화하기")
        click("설정으로")
        assertEquals(listOf(CopilotAction.START_CONVERSATION, CopilotAction.OPEN_SETTINGS), actions)
    }

    @Test
    fun failedDisconnectKeepsTheAccountAndPendingDisconnectCannotRepeat() {
        var state by mutableStateOf(CopilotUiState.Disconnect("@test-user", error = "연결을 해제하지 못했어요."))
        val actions = mutableListOf<CopilotAction>()
        compose.setContent { MobiMonTheme { CopilotConnectionScreen(state, actions::add, interactionAllowed = true) } }
        compose.onNodeWithText("@test-user").assertExists()
        compose.onNodeWithText("연결을 해제하지 못했어요.").assertExists()
        click("연결 유지")
        click("연결 해제")
        assertEquals(listOf(CopilotAction.KEEP_CONNECTION, CopilotAction.DISCONNECT), actions)
        compose.runOnIdle { state = state.copy(disconnecting = true, error = null) }
        compose
            .onNodeWithText("연결 해제")
            .assertIsDisplayed()
            .assertIsNotEnabled()
            .performClick()
        assertEquals(2, actions.size)
    }

    @Test
    fun largeTextKeepsActionsReachableAndKeyboardOperable() {
        val actions = mutableListOf<CopilotAction>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                MobiMonTheme {
                    CopilotConnectionScreen(
                        CopilotUiState.Introduction(),
                        actions::add,
                        interactionAllowed = true,
                    )
                }
            }
        }
        compose
            .onNodeWithText("QR로 연결하기")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(76.dp)
            .performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("QR로 연결하기").assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        assertEquals(listOf(CopilotAction.REQUEST_CODE), actions)
        compose
            .onNodeWithText("나중에")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun largeTextLabelsRenderWithoutClipping() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                MobiMonTheme { CopilotConnectionScreen(CopilotUiState.Introduction(), {}, interactionAllowed = true) }
            }
        }
        listOf("QR로 연결하기", "나중에", "GitHub Copilot").forEach { label ->
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            val layout = mutableListOf<TextLayoutResult>()
            compose
                .onNodeWithText(label, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
            val result = layout.single()
            assertFalse("Clipped height: $label", result.didOverflowHeight)
            repeat(result.lineCount) { line ->
                assertFalse("Ellipsized label: $label", result.isLineEllipsized(line))
                val lineWidth = result.getLineRight(line) - result.getLineLeft(line)
                assertTrue(
                    "Clipped width: $label ($lineWidth > ${result.size.width})",
                    lineWidth <= result.size.width + 1,
                )
            }
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun referenceScreensKeepPanelProportionsAndProduceReviewImages() {
        var state: CopilotUiState by mutableStateOf(CopilotUiState.Introduction())
        val actions = mutableListOf<CopilotAction>()
        val referenceActions =
            mapOf(
                "P51-introduction" to listOf("QR로 연결하기" to CopilotAction.REQUEST_CODE, "나중에" to CopilotAction.CANCEL),
                "P52-waiting" to listOf("QR 스캔이 어려워요" to CopilotAction.SHOW_ADDRESS, "승인했어요" to CopilotAction.RECHECK),
                "P52C-address" to listOf("QR로 돌아가기" to CopilotAction.SHOW_QR, "승인했어요" to CopilotAction.RECHECK),
                "P52B-expired" to listOf("새 QR 받기" to CopilotAction.REQUEST_CODE),
                "P53-connected" to
                    listOf("모비와 대화하기" to CopilotAction.START_CONVERSATION, "설정으로" to CopilotAction.OPEN_SETTINGS),
                "P54-reconnect" to listOf("QR로 다시 연결" to CopilotAction.REQUEST_CODE, "나중에" to CopilotAction.CANCEL),
                "P56-access" to listOf("GitHub에서 확인" to CopilotAction.REVIEW_ACCESS, "다시 확인" to CopilotAction.RECHECK),
                "P55-disconnect" to
                    listOf("연결 유지" to CopilotAction.KEEP_CONNECTION, "연결 해제" to CopilotAction.DISCONNECT),
            )
        lateinit var view: View
        compose.setContent {
            val currentView = LocalView.current
            SideEffect { view = currentView }
            MobiMonTheme {
                CopilotConnectionScreen(
                    state,
                    actions::add,
                    interactionAllowed = true,
                    qrCode = painterResource(R.drawable.copilot_preview_qr),
                )
            }
        }
        val directory = File("build/reports/copilot-ui").apply { mkdirs() }
        copilotPreviewStates.forEach { (name, sample) ->
            compose.runOnIdle { state = sample }
            val reference = compose.onNodeWithTag("copilot-reference").fetchSemanticsNode().boundsInRoot
            val companion = compose.onNodeWithTag("copilot-companion").fetchSemanticsNode().boundsInRoot
            val panel = compose.onNodeWithTag("copilot-panel").fetchSemanticsNode().boundsInRoot
            assertEquals(2560f / 1184f, reference.width / reference.height, 0.01f)
            assertEquals(884f / 2560f, companion.width / reference.width, 0.01f)
            assertTrue(panel.right <= reference.right)
            assertTrue(panel.bottom <= reference.bottom)
            compose.runOnIdle {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                File(directory, "$name.png").outputStream().use {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                bitmap.recycle()
            }
            referenceActions.getValue(name).forEach { (label, action) ->
                compose
                    .onNodeWithText(label)
                    .assertIsDisplayed()
                    .assertHeightIsAtLeast(76.dp)
                    .performClick()
                assertEquals(action, actions.last())
            }
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun authenticationStatesRenderWithoutClaimingConversationReadiness() {
        renderAuthenticationStates(1f, "reference")
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun authenticationStatesReflowWithEnlargedText() {
        renderAuthenticationStates(1.6f, "enlarged-text")
    }

    private fun renderAuthenticationStates(
        fontScale: Float,
        label: String,
    ) {
        var state: CopilotUiState by mutableStateOf(CopilotUiState.AuthenticationStatus(pending = true))
        val actions = mutableListOf<CopilotAction>()
        lateinit var view: View
        compose.setContent {
            val currentView = LocalView.current
            SideEffect { view = currentView }
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MobiMonTheme {
                    CopilotConnectionScreen(state, actions::add, interactionAllowed = true)
                }
            }
        }
        val states =
            listOf(
                "loading" to CopilotUiState.AuthenticationStatus(pending = true),
                "authenticated" to CopilotUiState.AuthenticationStatus(account = "@mobimon-driver"),
                "network" to
                    CopilotUiState.AuthenticationStatus(
                        problem = com.monsters.mobimon.core.domain.AuthenticationProblem.NETWORK,
                    ),
            )
        val directory = File("build/reports/copilot-ui").apply { mkdirs() }
        states.forEach { (name, sample) ->
            compose.runOnIdle { state = sample }
            compose.waitForIdle()
            if (sample.account != null) {
                compose.onNodeWithText("GitHub 계정 인증이 완료됐어요.").assertExists()
                compose.onNodeWithText("Copilot 대화 이용 가능", substring = true).assertDoesNotExist()
                val chat = compose.onNodeWithText("모비와 대화하기")
                if (label == "enlarged-text") chat.performScrollTo()
                chat.assertIsDisplayed().assertIsEnabled().performClick()
                assertEquals(CopilotAction.START_CONVERSATION, actions.last())
                val settings = compose.onNodeWithText("설정으로")
                if (label == "enlarged-text") settings.performScrollTo()
                settings.assertIsDisplayed().performClick()
                assertEquals(CopilotAction.OPEN_SETTINGS, actions.last())
                if (label == "reference") {
                    val reference = compose.onNodeWithTag("copilot-reference").fetchSemanticsNode().boundsInRoot
                    val bounds = chat.fetchSemanticsNode().boundsInRoot
                    assertEquals(reference.left + 1064f, bounds.left, 1f)
                    assertEquals(reference.top + 992f, bounds.top, 1f)
                    assertEquals(816f, bounds.width, 1f)
                    assertEquals(112f, bounds.height, 1f)
                }
                val disconnect = compose.onNodeWithText("연결 해제")
                if (label == "enlarged-text") disconnect.performScrollTo()
                disconnect.assertHeightIsAtLeast(76.dp).assertIsDisplayed().performClick()
                assertEquals(CopilotAction.CONFIRM_DISCONNECT, actions.last())
            } else {
                compose.onNodeWithText("모비와 대화하기").assertDoesNotExist()
            }
            compose.runOnIdle {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                File(directory, "authentication-$label-$name.png").outputStream().use {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                bitmap.recycle()
            }
        }
    }

    private fun show(
        state: CopilotUiState,
        action: (CopilotAction) -> Unit = {
        },
        allowed: Boolean = true,
        qr: Boolean = true,
    ) {
        compose.setContent {
            val currentView = LocalView.current
            SideEffect { reviewView = currentView }
            MobiMonTheme {
                CopilotConnectionScreen(
                    state,
                    action,
                    interactionAllowed = allowed,
                    qrCode = if (qr) painterResource(R.drawable.copilot_preview_qr) else null,
                )
            }
        }
    }

    private fun click(text: String) = compose.onNodeWithText(text).assertIsDisplayed().performClick()
}
