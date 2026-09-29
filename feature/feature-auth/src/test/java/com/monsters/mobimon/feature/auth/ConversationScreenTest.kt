package com.monsters.mobimon.feature.auth

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.view.View
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.monsters.mobimon.core.domain.ConversationProblem
import com.monsters.mobimon.core.ui.LocalMobiMonMotionEnabled
import com.monsters.mobimon.core.ui.MobiMonTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-rKR-w2560dp-h1248dp-mdpi")
@OptIn(ExperimentalTestApi::class)
class ConversationScreenTest {
    @get:Rule val compose = createComposeRule()
    private var state by mutableStateOf(ConversationUiState(ConversationConnection.READY))
    private var draft by mutableStateOf(TextFieldValue())
    private var allowed by mutableStateOf(true)
    private var height by mutableStateOf(1184.dp)
    private var sends = 0
    private var cancellations = 0
    private var homeReturns = 0
    private var checks = 0
    private var retries = 0
    private var connectionOpens = 0
    private var voiceStarts = 0
    private var voiceStops = 0
    private var voiceCancellations = 0
    private lateinit var view: View

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun unreadableEmptyHistoryOffersReloadAndNewConversation() {
        state = state.copy(failed = true, problem = ConversationProblem.STORAGE)
        show()
        compose.onNodeWithText("대화 기록 오류").assertIsDisplayed()
        compose.onNodeWithText("불러오기").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("새 대화").assertIsDisplayed().assertIsEnabled()
        capture("storage-error")
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun copilotDisclosureStaysVisibleDuringVoicePermissionAndSignOut() {
        state = state.copy(voice = VoiceInputState(available = true))
        show()
        val disclosure = "대화와 확인된 이름·시간·차량 상태 신호는 GitHub Copilot에 전달돼요. 디버거 사용 중에는 테스트 값이 전달돼요. 답변은 부정확할 수 있어요."
        compose.onNodeWithText(disclosure).assertIsDisplayed()
        capture("composer-placeholder")

        compose.runOnIdle { state = state.copy(voice = state.voice.copy(phase = VoiceInputPhase.PERMISSION)) }
        compose.onNodeWithText(disclosure).assertIsDisplayed()
        compose.onNodeWithTag("chat-voice-hint").assertDoesNotExist()
        capture("voice-permission-disclosure")

        compose.runOnIdle {
            state =
                state.copy(
                    connection = ConversationConnection.SIGNED_OUT,
                    voice = VoiceInputState(available = true, problem = VoiceInputProblem.PERMISSION),
                )
        }
        compose.onNodeWithText(disclosure).assertIsDisplayed()
        val hint = compose.onNodeWithTag("chat-voice-hint").fetchSemanticsNode().boundsInRoot
        val composer = compose.onNodeWithTag("chat-composer").fetchSemanticsNode().boundsInRoot
        assertTrue(hint.bottom <= composer.top)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun voiceInputBlocksSuggestionsUntilCancellationRestoresEditing() {
        state = state.copy(voice = VoiceInputState(available = true))
        draft = TextFieldValue("original draft")
        show()
        val chooseSuggestion =
            compose
                .onNodeWithText("오늘 하루 이야기할래")
                .fetchSemanticsNode()
                .config[SemanticsActions.OnClick]
                .action!!
        for (phase in listOf(
            VoiceInputPhase.PERMISSION,
            VoiceInputPhase.STARTING,
            VoiceInputPhase.LISTENING,
            VoiceInputPhase.STOPPING,
        )) {
            compose.runOnIdle { state = state.copy(voice = state.voice.copy(phase = phase)) }
            compose.onNodeWithText("오늘 하루 이야기할래").assertDoesNotExist()
            compose.onNodeWithText("기분 좋아지는 얘기 해줘").assertDoesNotExist()
            compose.runOnIdle {
                chooseSuggestion()
                assertEquals("original draft", draft.text)
                assertEquals(0, sends)
            }
        }
        compose.runOnIdle { state = state.copy(voice = state.voice.copy(phase = VoiceInputPhase.LISTENING)) }
        capture("empty-voice-recording")
        compose.onNodeWithContentDescription("음성 입력 취소").performClick()
        compose.onNodeWithTag("chat-input").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("original draft", draft.text)
            assertEquals(1, voiceCancellations)
        }
        capture("empty-voice-cancelled")
        compose.onNodeWithText("오늘 하루 이야기할래").performClick()
        compose.onNodeWithTag("chat-input").assertIsFocused()
        compose.runOnIdle {
            assertEquals("오늘 하루 이야기할래", draft.text)
            assertEquals(0, sends)
        }
    }

    @Test
    fun storageResetBlocksComposerSuggestionAndVoiceUntilItFinishes() {
        state = state.copy(storageBusy = true, voice = VoiceInputState(available = true))
        draft = TextFieldValue("old draft")
        show()
        compose.onNodeWithTag("chat-input").assertIsNotEnabled()
        compose.onNodeWithTag("chat-send").assertIsNotEnabled()
        compose.onNodeWithContentDescription("음성으로 입력").assertIsNotEnabled()
        compose.onNodeWithText("오늘 하루 이야기할래").performClick()
        compose.runOnIdle {
            assertEquals("old draft", draft.text)
            assertEquals(0, voiceStarts)
        }

        compose.runOnIdle { state = state.copy(storageBusy = false) }
        compose.onNodeWithTag("chat-input").assertIsEnabled()
        compose.onNodeWithContentDescription("음성으로 입력").assertIsEnabled()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun enlargedVoiceInputRestoresSuggestionsAfterCancellationAtAaosDensity() {
        state = state.copy(voice = VoiceInputState(available = true, phase = VoiceInputPhase.PERMISSION))
        draft = TextFieldValue("original draft")
        show(fontScale = 1.6f, density = 10f / 7f)
        compose.onNodeWithText("오늘 하루 이야기할래").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(voice = state.voice.copy(phase = VoiceInputPhase.LISTENING)) }
        compose.onNodeWithText("오늘 하루 이야기할래").assertDoesNotExist()
        capture("empty-voice-recording-enlarged-aaos")
        compose.onNodeWithContentDescription("음성 입력 취소").performClick()
        compose.onNodeWithText("오늘 하루 이야기할래").assertIsDisplayed().performClick()
        compose.onNodeWithTag("chat-input").assertIsFocused()
        compose.runOnIdle {
            assertEquals("오늘 하루 이야기할래", draft.text)
            assertEquals(0, sends)
        }
    }

    @Test
    fun restrictedConversationShowsParkingNoticeInHeader() {
        allowed = false
        show()
        val badge =
            compose
                .onNodeWithContentDescription(
                    "주차 후 이용",
                ).assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        assertEquals(51f, badge.top, 1f)
        assertEquals(2416f, badge.right, 1f)
        val icon = compose.onNodeWithTag("chat-parking-icon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val text = compose.onNodeWithText("주차 후 이용", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(badge.center.x, (icon.left + text.right) / 2f, 1f)
        assertEquals(badge.center.y, icon.center.y, 1f)
        assertEquals(badge.center.y, text.center.y, 1f)
    }

    @Test
    fun suggestionsOnlyFillDraftAndExplicitSendCommitsCompositionWithoutDuplicates() {
        show()
        compose.onNodeWithTag("chat-send").assertIsNotEnabled()
        compose.onNodeWithText("오늘 하루 이야기할래").performClick()
        compose.onNodeWithTag("chat-input").assertIsFocused()
        compose.runOnIdle {
            assertEquals(0, sends)
            assertEquals("오늘 하루 이야기할래", draft.text)
        }
        compose.onNodeWithTag("chat-input").performImeAction()
        compose.onNodeWithTag("chat-input").performImeAction()
        compose.runOnIdle { assertEquals(1, sends) }
        compose.onNodeWithContentDescription("답변 기다리기 취소").performClick()
        compose.runOnIdle {
            assertEquals(1, cancellations)
            draft = TextFieldValue("한", TextRange(1), TextRange(0, 1))
        }
        compose.onNodeWithTag("chat-input").performImeAction()
        compose.runOnIdle {
            assertEquals(2, sends)
            assertEquals("한", state.messages.single().text)
        }
    }

    @Test
    fun unverifiedConnectionBlocksSendAndSignedOutAndRestrictedStatesBlockIt() {
        state = ConversationUiState(ConversationConnection.UNAVAILABLE)
        show()
        compose.onNodeWithTag("chat-input").performTextInput("작성 중")
        compose.onNodeWithTag("chat-send").assertIsNotEnabled()
        compose.onNodeWithText("Copilot 연결됨").assertDoesNotExist()
        compose.onNodeWithTag("chat-auth-badge").assertContentDescriptionEquals("Copilot 확인 중")
        compose.runOnIdle { state = state.copy(connection = ConversationConnection.SIGNED_OUT) }
        compose.onNodeWithTag("chat-auth-badge").assertContentDescriptionEquals("Copilot 확인 중")
        compose.onNodeWithTag("chat-send").assertIsNotEnabled()
        compose.runOnIdle { allowed = false }
        compose.onNodeWithTag("chat-parking-dialog").assertIsDisplayed()
        compose.onNodeWithTag("chat-input").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals("작성 중", draft.text)
            assertEquals(0, sends)
        }
    }

    @Test
    fun connectionBadgeUsesOnlyConnectedAndCheckingLabels() {
        var connection by mutableStateOf(ConversationConnection.CHECKING)
        var problem by mutableStateOf<ConversationProblem?>(null)
        compose.setContent {
            MobiMonTheme {
                ConversationAuthBadge(connection = connection, problem = problem, scale = 1f)
            }
        }

        val badge = compose.onNodeWithTag("chat-auth-badge")
        badge.assertContentDescriptionEquals("Copilot 확인 중")
        compose.runOnIdle { connection = ConversationConnection.SIGNED_OUT }
        badge.assertContentDescriptionEquals("Copilot 확인 중")
        compose.runOnIdle { connection = ConversationConnection.UNAVAILABLE }
        badge.assertContentDescriptionEquals("Copilot 확인 중")
        compose.runOnIdle {
            connection = ConversationConnection.READY
            problem = ConversationProblem.NETWORK
        }
        badge.assertContentDescriptionEquals("Copilot 확인 중")
        compose.runOnIdle { problem = ConversationProblem.TIMEOUT }
        badge.assertContentDescriptionEquals("Copilot 확인 중")
        compose.runOnIdle { problem = null }
        badge.assertContentDescriptionEquals("Copilot 연결됨")
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun networkFailureShowsModalAndRecheckNeverSendsDraft() {
        state = ConversationUiState(ConversationConnection.UNAVAILABLE, connectionProblem = ConversationProblem.NETWORK)
        draft = TextFieldValue("작성 중")
        show()
        capture("network-error")
        compose.onNodeWithTag("chat-network-dialog").assertIsDisplayed()
        compose.onNodeWithText("네트워크 연결을 확인해 주세요").assertIsDisplayed()
        compose.onNodeWithTag("chat-send").assertDoesNotExist()
        compose.onNodeWithTag("chat-network-retry").performClick()
        compose.runOnIdle {
            assertEquals(1, checks)
            assertEquals(0, sends)
            assertEquals("작성 중", draft.text)
        }
        compose.onNodeWithText("Copilot 연결을 다시 확인하는 중").assertIsDisplayed()
        val ring =
            compose
                .onNodeWithTag(
                    "chat-connection-ring",
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                .boundsInRoot
        assertEquals(688f, ring.left, 1f)
        assertEquals(342f, ring.top, 1f)
        assertEquals(64f, ring.width, 1f)
        assertEquals(64f, ring.height, 1f)
        compose.onNodeWithTag("chat-network-retry").assertIsNotEnabled()
        compose.onNodeWithTag("chat-network-home").assertIsEnabled()
        capture("network-checking")
        compose.runOnIdle { state = state.copy(connection = ConversationConnection.READY, connectionRetrying = false) }
        compose.onNodeWithTag("chat-network-dialog").assertDoesNotExist()
        compose.onNodeWithTag("chat-send").assertIsEnabled()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun communicationTimeoutUsesNetworkRecoveryAtReferenceSize() {
        showTimeoutRecovery(1f)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun communicationTimeoutUsesNetworkRecoveryWithEnlargedText() {
        showTimeoutRecovery(1.6f)
    }

    private fun showTimeoutRecovery(fontScale: Float) {
        state = ConversationUiState(ConversationConnection.UNAVAILABLE, connectionProblem = ConversationProblem.TIMEOUT)
        draft = TextFieldValue("대기 중인 초안")
        show(fontScale = fontScale)
        compose.onNodeWithText("네트워크 연결을 확인해 주세요").assertIsDisplayed()
        compose.onNodeWithText("Copilot 답변이 지연됐어요").assertDoesNotExist()
        capture(if (fontScale == 1f) "network-timeout" else "network-timeout-enlarged")
        compose.onNodeWithTag("chat-network-retry").performClick()
        compose.onNodeWithText("Copilot 연결을 다시 확인하는 중").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, checks)
            assertEquals(0, sends)
            assertEquals("대기 중인 초안", draft.text)
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun voiceAvailabilityDoesNotBypassNetworkPopup() {
        showVoiceNetworkPopup(1f, ConversationProblem.NETWORK)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun voiceAvailabilityDoesNotBypassTimeoutPopupWithEnlargedText() {
        showVoiceNetworkPopup(1.6f, ConversationProblem.TIMEOUT)
    }

    private fun showVoiceNetworkPopup(
        fontScale: Float,
        problem: ConversationProblem,
    ) {
        state =
            state.copy(
                connection = ConversationConnection.UNAVAILABLE,
                connectionProblem = problem,
                voice = VoiceInputState(available = true),
            )
        draft = TextFieldValue("보존할 초안")
        show(fontScale = fontScale)
        compose.onNodeWithTag("chat-network-dialog").assertIsDisplayed()
        compose.onNodeWithTag("chat-inline-failure").assertDoesNotExist()
        compose.onNodeWithContentDescription("음성으로 입력").assertDoesNotExist()
        capture(if (fontScale == 1f) "voice-network-popup" else "voice-timeout-popup-enlarged")
        compose.onNodeWithTag("chat-network-retry").performClick()
        compose.onNodeWithTag("chat-network-dialog").assertIsDisplayed()
        compose.onNodeWithTag("chat-network-retry").assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(1, checks)
            assertEquals(0, sends)
            assertEquals("보존할 초안", draft.text)
            state = state.copy(connection = ConversationConnection.READY, connectionRetrying = false)
        }
        compose.onNodeWithTag("chat-network-dialog").assertDoesNotExist()
        compose.onNodeWithText("Copilot 연결됨").assertIsDisplayed()
        compose.onNodeWithTag("chat-input").assertIsDisplayed()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun failedMessageNetworkErrorShowsDialogThenKeepsRetryAndEditReachable() {
        state =
            ConversationUiState(
                ConversationConnection.UNAVAILABLE,
                messages = listOf(ConversationMessage("attempt", "다시 보낼 내용", true)),
                failed = true,
                problem = ConversationProblem.NETWORK,
                connectionProblem = ConversationProblem.NETWORK,
                voice = VoiceInputState(available = true),
            )
        draft = TextFieldValue()
        show()
        capture("message-network-failed")
        compose.onNodeWithTag("chat-network-dialog").assertIsDisplayed()
        compose.onNodeWithTag("chat-inline-failure").assertDoesNotExist()
        compose.onNodeWithTag("chat-network-retry").performClick()
        compose.runOnIdle { assertEquals(1, checks) }
        compose.runOnIdle { state = state.copy(connection = ConversationConnection.READY, connectionRetrying = false) }
        compose.onNodeWithTag("chat-network-dialog").assertDoesNotExist()
        compose.onNodeWithTag("chat-user-bubble").assertIsDisplayed()
        compose.onNodeWithTag("chat-inline-failure").assertIsDisplayed()
        compose.onNodeWithText("Copilot 연결됨").assertIsDisplayed()
        compose.runOnIdle { assertEquals("", draft.text) }
        capture("message-network-recovered")
        compose.runOnIdle { draft = draft.copy(selection = TextRange(0)) }
        compose.onNodeWithTag("chat-inline-failure").assertIsDisplayed()
        compose.onNodeWithText("다시 보내기").performClick()
        compose.runOnIdle {
            assertEquals(1, retries)
            assertEquals(1, checks)
        }
        compose.onNodeWithText("내용 수정").performClick()
        compose.runOnIdle { assertEquals("다시 보낼 내용", draft.text) }
        compose.onNodeWithTag("chat-user-bubble").assertDoesNotExist()
    }

    @Test fun usageFailureShowsOnlyCopilotUsageDialog() {
        state =
            ConversationUiState(
                ConversationConnection.UNAVAILABLE,
                messages = listOf(ConversationMessage("attempt", "사용량 확인", true)),
                failed = true,
                problem = ConversationProblem.USAGE,
                connectionProblem = ConversationProblem.USAGE,
            )
        show()

        compose.onNodeWithText("Copilot 사용량을 확인해 주세요").assertIsDisplayed()
        compose.onNodeWithText("GitHub에서 사용량을 확인하고, 한도 갱신 후 대화를 다시 열어 주세요.").assertIsDisplayed()
        compose.onNodeWithTag("chat-inline-failure").assertDoesNotExist()
        compose.onNodeWithTag("chat-network-retry").assertDoesNotExist()
        compose.onNodeWithTag("chat-network-home").performClick()
        compose.runOnIdle {
            assertEquals(1, homeReturns)
            assertEquals(0, checks)
            assertEquals(0, sends)
        }
    }

    @Test
    fun shortExchangeKeepsUserAndCompanionBubblesVisible() {
        state = state.copy(messages = messages)
        show()
        compose.onNodeWithText("오늘은 조금 피곤한 하루였어.").assertIsDisplayed()
        compose.onNodeWithText("오늘 하루도 수고했어요.", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("chat-new-action").assertIsDisplayed()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun accessCheckFailureExplainsPermissionChangeAndReturnsHome() {
        state = ConversationUiState(ConversationConnection.UNAVAILABLE, connectionProblem = ConversationProblem.ACCESS)
        show()
        capture("connection-access")
        compose.onNodeWithText("Copilot 권한을 확인해 주세요").assertIsDisplayed()
        compose.onNodeWithText("GitHub가 이 앱의 대화 요청을 허용하지 않았어요.", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("chat-send").assertDoesNotExist()
        val icon =
            compose
                .onNodeWithTag(
                    "chat-connection-icon",
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                .boundsInRoot
        assertEquals(112f, icon.width, 1f)
        val body = compose.onNodeWithTag("chat-connection-body").fetchSemanticsNode().boundsInRoot
        val instruction = compose.onNodeWithTag("chat-connection-instruction").fetchSemanticsNode().boundsInRoot
        val preserved = compose.onNodeWithTag("chat-connection-preserved").fetchSemanticsNode().boundsInRoot
        assertEquals(54f, instruction.top - body.top, 1f)
        assertEquals(87f, preserved.top - instruction.top, 1f)
        compose.onNodeWithTag("chat-network-retry").assertDoesNotExist()
        compose.onNodeWithTag("chat-network-home").performClick()
        compose.runOnIdle {
            assertEquals(1, homeReturns)
            assertEquals(0, checks)
        }
    }

    @Test
    fun accountCheckFailureOpensConnectionManagement() {
        state = ConversationUiState(ConversationConnection.UNAVAILABLE, connectionProblem = ConversationProblem.ACCOUNT)
        show()
        compose.onNodeWithText("Copilot 계정을 확인해 주세요").assertIsDisplayed()
        compose.onNodeWithText("연결 안내 열기").performClick()
        compose.runOnIdle {
            assertEquals(1, connectionOpens)
            assertEquals(0, checks)
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun providerCheckFailureExplainsErrorAndOffersRecheck() {
        state =
            ConversationUiState(ConversationConnection.UNAVAILABLE, connectionProblem = ConversationProblem.PROVIDER)
        show()
        capture("connection-provider")
        compose.onNodeWithText("Copilot이 지원하지 않는 응답을 보냈어요.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("다시 확인").performClick()
        compose.runOnIdle { assertEquals(1, checks) }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun parkingLossShowsBlockingDialogWithoutClearingDraftBeforeHomeCallback() {
        draft = TextFieldValue("주차 후 보낼 메시지")
        allowed = false
        show()

        capture("parking-required")
        compose.onNodeWithText("주차 후 대화를 이어가요").assertIsDisplayed()
        compose.onNodeWithText("대화와 전송은 주차 상태에서만 사용할 수 있어요.").assertIsDisplayed()
        val dialog = compose.onNodeWithTag("chat-parking-dialog").fetchSemanticsNode().boundsInRoot
        assertEquals(600f, dialog.left, 1f)
        assertEquals(254f, dialog.top, 1f)
        assertEquals(1360f, dialog.width, 1f)
        assertEquals(740f, dialog.height, 1f)
        val badge =
            compose
                .onNodeWithContentDescription("주차 후 이용")
                .fetchSemanticsNode()
                .boundsInRoot
        assertEquals(2144f, badge.left, 1f)
        assertEquals(51f, badge.top, 1f)
        assertEquals(272f, badge.width, 1f)
        assertEquals(60f, badge.height, 1f)
        compose.onNodeWithTag("chat-send").assertDoesNotExist()
        compose.onNodeWithTag("chat-parking-home").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, homeReturns)
            assertEquals("주차 후 보낼 메시지", draft.text)
            allowed = true
        }
        compose.onNodeWithTag("chat-parking-dialog").assertDoesNotExist()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun parkingDialogKeepsHomeReachableWithEnlargedTextAtAaosDensity() {
        allowed = false
        show(fontScale = 1.6f, density = 10f / 7f)

        compose.onNodeWithText("주차 후 대화를 이어가요").assertIsDisplayed()
        compose
            .onNodeWithTag("chat-parking-home")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(76.dp)
        capture("parking-required-enlarged-aaos")
    }

    @Test
    fun sendIsReachableUsingKeyboard() {
        draft = TextFieldValue("안녕")
        show()
        compose
            .onNodeWithTag("chat-send")
            .assertHeightIsAtLeast(76.dp)
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(1, sends) }
    }

    @Test
    fun lostProviderAccessKeepsCompletedRepliesVisibleWithRecovery() {
        state = ConversationUiState(ConversationConnection.UNAVAILABLE, messages = messages)
        show()
        compose.onNodeWithText(messages.last().text).assertIsDisplayed()
        compose.onNodeWithText("Copilot 연결 확인").assertDoesNotExist()
        compose.onNodeWithTag("chat-send").assertIsNotEnabled()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun cleanInitialScreenAndRecoveryRemainAccessibleAtAaosDensity() {
        state = ConversationUiState(ConversationConnection.UNAVAILABLE)
        draft = TextFieldValue("보내기 전 초안")
        show(density = 10f / 7f)
        capture("initial-aaos")
        compose.onNodeWithTag("chat-send").assertIsNotEnabled()
        compose.onNodeWithText("확인하고 Copilot 연결").assertDoesNotExist()
        compose.onNodeWithText("Copilot 연결 확인").assertDoesNotExist()
        compose.onNodeWithText("GitHub 개인정보 처리방침").assertDoesNotExist()
        state = state.copy(connection = ConversationConnection.READY, messages = messages)
        capture("session-actions-aaos")
        compose
            .onNodeWithText("새 대화")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(76.dp)
            .performClick()
        compose.runOnIdle { assertTrue(state.messages.isEmpty()) }
        compose.runOnIdle { state = state.copy(failed = true, problem = ConversationProblem.ACCESS) }
        capture("access-error-aaos")
        compose.onNodeWithText("다시 확인").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(problem = ConversationProblem.SERVICE) }
        compose.onNodeWithText("Copilot 서비스가 응답하지 못했어요.", substring = true).assertIsDisplayed()
        capture("service-error-aaos")
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun initialComposerAndLengthFeedbackWorkWithEnlargedTextAndKeyboard() {
        state = ConversationUiState(ConversationConnection.UNAVAILABLE)
        height = 940.dp
        show(fontScale = 1.6f)
        compose.onNodeWithTag("chat-input").assertIsDisplayed()
        capture("initial-enlarged-keyboard")
        compose.runOnIdle {
            state = state.copy(connection = ConversationConnection.READY)
            draft = TextFieldValue("x".repeat(4001))
        }
        compose.onNodeWithTag("chat-send").assertIsNotEnabled()
        compose.onNodeWithText("메시지를 4,000자 이하로 줄여 주세요.").assertIsDisplayed()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun referenceStatesAndKeyboardResizeProduceReviewImages() {
        show()
        capture("empty")
        compose.onNodeWithText("모비와의 대화").assertIsDisplayed()
        compose.onNodeWithText("오늘도 함께 쉬어 가요.").assertIsDisplayed()
        compose.onNodeWithText("오늘 하루 이야기할래").assertIsDisplayed()
        compose.onNodeWithText("기분 좋아지는 얘기 해줘").assertIsDisplayed()
        compose.onNodeWithText("모비는 오늘 어땠어?").assertIsDisplayed()
        val companion = compose.onNodeWithTag("chat-companion").fetchSemanticsNode().boundsInRoot
        val panel = compose.onNodeWithTag("chat-panel").fetchSemanticsNode().boundsInRoot
        assertEquals(72f, companion.left, 1f)
        assertEquals(196f, companion.top, 1f)
        assertEquals(680f, companion.width, 1f)
        assertEquals(964f, companion.height, 1f)
        assertEquals(796f, panel.left, 1f)
        assertEquals(34f, panel.top, 1f)
        assertEquals(1692f, panel.width, 1f)
        assertEquals(1126f, panel.height, 1f)
        val avatar = compose.onNodeWithTag("chat-avatar").fetchSemanticsNode().boundsInRoot
        assertEquals(100f, avatar.left, 1f)
        assertEquals(336f, avatar.top, 1f)
        assertEquals(624f, avatar.width, 1f)
        val parking = compose.onNodeWithContentDescription("주차 확인됨").fetchSemanticsNode().boundsInRoot
        assertEquals(2146f, parking.left, 1f)
        assertEquals(51f, parking.top, 1f)
        assertEquals(258f, parking.width, 1f)
        assertEquals(60f, parking.height, 1f)
        val parkingIcon =
            compose
                .onNodeWithTag(
                    "chat-parking-icon",
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                .boundsInRoot
        val parkingText = compose.onNodeWithText("주차 확인됨", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(parking.center.x, (parkingIcon.left + parkingText.right) / 2f, 1f)
        assertEquals(parking.center.y, parkingIcon.center.y, 1f)
        assertEquals(parking.center.y, parkingText.center.y, 1f)
        val auth = compose.onNodeWithTag("chat-auth-badge").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("chat-auth-badge").assertContentDescriptionEquals("Copilot 연결됨")
        assertEquals(1887f, auth.left, 1f)
        assertEquals(51f, auth.top, 1f)
        assertEquals(244f, auth.width, 1f)
        assertEquals(60f, auth.height, 1f)
        compose.runOnIdle { state = state.copy(messages = referenceMessages) }
        capture("messages")
        val userBubble =
            compose
                .onAllNodesWithTag("chat-user-bubble")
                .onFirst()
                .fetchSemanticsNode()
                .boundsInRoot
        val friendBubble =
            compose
                .onAllNodesWithTag("chat-friend-bubble")
                .onFirst()
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue(userBubble.width < 500f)
        assertTrue(friendBubble.width < 900f)
        compose.onNodeWithText("기분 전환할 이야기 해 줘").assertDoesNotExist()
        val newChat = compose.onNodeWithTag("chat-new-action").fetchSemanticsNode().boundsInRoot
        assertEquals(2212f, newChat.left, 1f)
        assertEquals(220f, newChat.width, 1f)
        compose.runOnIdle {
            state =
                state.copy(
                    messages = referenceMessages + ConversationMessage("last-user", "모비는 뭐가 좋아?", true),
                    replyPending = true,
                )
        }
        capture("reply-pending")
        compose.onNodeWithText("오늘은 조금 피곤한 하루였어.").assertIsDisplayed()
        val firstPendingBubble =
            compose
                .onAllNodesWithTag(
                    "chat-user-bubble",
                ).onFirst()
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue(firstPendingBubble.top > 180f)
        val pendingBubble = compose.onNodeWithTag("chat-pending-bubble").fetchSemanticsNode().boundsInRoot
        assertTrue(pendingBubble.width < 200f)
        compose.runOnIdle {
            height = 940.dp
            state =
                state.copy(
                    messages = referenceMessages,
                    replyPending = false,
                )
            draft = TextFieldValue("모비는 뭐가 좋아?")
        }
        capture("keyboard-input")
        compose.onNodeWithText("오늘은 조금 피곤한 하루였어.").assertIsDisplayed()
        compose
            .onNodeWithText(
                "대화와 확인된 이름·시간·차량 상태 신호는 GitHub Copilot에 전달돼요. 디버거 사용 중에는 테스트 값이 전달돼요. 답변은 부정확할 수 있어요.",
            ).assertIsDisplayed()
        val resized = compose.onNodeWithTag("chat-panel").fetchSemanticsNode().boundsInRoot
        val composer = compose.onNodeWithTag("chat-composer").fetchSemanticsNode().boundsInRoot
        assertEquals(panel.left, resized.left, 1f)
        assertEquals(panel.top, resized.top, 1f)
        assertTrue(composer.bottom <= resized.bottom)
        assertEquals(882f, resized.height, 1f)
        assertEquals(796f, resized.left, 1f)
        assertEquals(34f, resized.top, 1f)
        assertEquals(916f, resized.bottom, 1f)
        compose.runOnIdle {
            height = 1184.dp
            state =
                state.copy(
                    messages = referenceMessages + ConversationMessage("last-user", "모비는 뭐가 좋아?", true),
                    failed = true,
                )
        }
        capture("connection-failed")
        val failureImage = BitmapFactory.decodeFile("build/reports/conversation-ui/connection-failed.png")
        try {
            // Exported SVG includes a 96px status bar; compare only the visible ink after removing it.
            assertPaintBounds(failureImage, 845..890, 920..965, Color.rgb(234, 184, 170), Rect(853, 928, 883, 958))
            assertPaintBounds(failureImage, 895..1180, 920..970, Color.rgb(234, 184, 170), Rect(903, 930, 1160, 956))
            assertPaintBounds(failureImage, 895..1420, 960..1005, Color.rgb(181, 197, 213), Rect(902, 969, 1372, 992))
        } finally {
            failureImage.recycle()
        }
        compose.onNodeWithText("오늘은 조금 피곤한 하루였어.").assertIsDisplayed()
        compose.onNodeWithTag("chat-companion").assertIsDisplayed()
        compose.onNodeWithTag("chat-panel").assertIsDisplayed()
        val failure = compose.onNodeWithTag("chat-inline-failure").fetchSemanticsNode().boundsInRoot
        assertEquals(852f, failure.left, 1f)
        assertEquals(929f, failure.top, 1f)
        val warning =
            compose
                .onNodeWithTag("chat-inline-warning-icon", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val failureTitle = compose.onNodeWithText("답변을 받지 못했어요").fetchSemanticsNode().boundsInRoot
        assertEquals(852f, warning.left, 1f)
        assertEquals(927f, warning.top, 1f)
        assertEquals(32f, warning.width, 1f)
        assertEquals(900f, failureTitle.left, 1f)
        assertEquals(920f, failureTitle.top, 1f)
        assertEquals(262f, failureTitle.width, 1f)
        val failureNote = compose.onNodeWithText("일시적인 연결 문제예요. 다시 시도해 주세요.").fetchSemanticsNode().boundsInRoot
        assertEquals(900f, failureNote.left, 1f)
        assertEquals(960f, failureNote.top, 1f)
        val retry =
            compose
                .onNodeWithTag(
                    "chat-inline-retry-visual",
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                .boundsInRoot
        val edit =
            compose
                .onNodeWithTag(
                    "chat-inline-edit-visual",
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                .boundsInRoot
        assertEquals(1974f, retry.left, 1f)
        assertEquals(933f, retry.top, 1f)
        assertEquals(230f, retry.width, 1f)
        assertEquals(52f, retry.height, 1f)
        assertEquals(2228f, edit.left, 1f)
        assertEquals(933f, edit.top, 1f)
        assertEquals(206f, edit.width, 1f)
        assertEquals(52f, edit.height, 1f)
        compose.onNodeWithText("내용 수정").performClick()
        compose.onNodeWithTag("chat-input").assertIsDisplayed()
        assertEquals(2, compose.onAllNodesWithTag("chat-user-bubble").fetchSemanticsNodes().size)
        compose.runOnIdle { assertEquals("모비는 뭐가 좋아?", draft.text) }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun enlargedTextKeepsComposerAndControlsVisible() {
        draft = TextFieldValue("긴 메시지를 작성하고 있어요. 줄 바꿈과 선택도 유지해요.")
        show(fontScale = 1.6f)
        compose.onNodeWithTag("chat-input").assertIsDisplayed()
        compose.onNodeWithTag("chat-send").assertIsDisplayed().assertHeightIsAtLeast(76.dp)
        capture("target-enlarged-text")
    }

    @Test
    fun enlargedParkingBadgesKeepIconAndLabelSeparate() {
        show(fontScale = 1.6f)

        fun assertSeparated(status: String) {
            val badge = compose.onNodeWithContentDescription(status).fetchSemanticsNode().boundsInRoot
            val icon =
                compose
                    .onNodeWithTag(
                        "chat-parking-icon",
                        useUnmergedTree = true,
                    ).fetchSemanticsNode()
                    .boundsInRoot
            val label = compose.onNodeWithText(status, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("Icon overlaps $status", icon.right < label.left)
            assertTrue("Icon escapes $status badge", icon.left >= badge.left)
            assertTrue("Label escapes $status badge", label.right <= badge.right)
        }

        assertSeparated("주차 확인됨")
        compose.runOnIdle { allowed = false }
        assertSeparated("주차 후 이용")
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = "ko-rKR-w800dp-h1000dp-mdpi")
    fun enlargedTextKeepsFailedTurnAndRecoveryActionsVisible() {
        state =
            ConversationUiState(
                ConversationConnection.READY,
                messages = listOf(ConversationMessage("attempt", "남겨 둔 질문", true)),
                failed = true,
                problem = ConversationProblem.TIMEOUT,
            )
        draft = TextFieldValue()
        height = 900.dp
        show(fontScale = 1.6f)

        compose.onNodeWithTag("chat-user-bubble").assertIsDisplayed()
        compose.onNodeWithTag("chat-inline-failure").assertIsDisplayed()
        compose.onNodeWithTag("chat-composer").assertIsDisplayed()
        compose.runOnIdle { assertEquals("", draft.text) }
        capture("failed-enlarged-text")
        compose.onNodeWithText("다시 보내기").performClick()
        compose.onNodeWithText("내용 수정").performClick()
        compose.runOnIdle {
            assertEquals(1, retries)
            assertEquals("남겨 둔 질문", draft.text)
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aaosDensityPreservesReferenceGeometryAndLargeTouchTargets() {
        state = state.copy(messages = messages)
        show(density = 10f / 7f)
        capture("messages-aaos")
        compose.onNodeWithTag("chat-send").assertHeightIsAtLeast(76.dp)
        val panel = compose.onNodeWithTag("chat-panel").fetchSemanticsNode().boundsInRoot
        assertEquals(796f, panel.left, 1f)
        assertEquals(34f, panel.top, 1f)
        assertEquals(1126f, panel.height, 1f)
        val send = compose.onNodeWithTag("chat-send-visual", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(68.53f, send.width, 1f)
        compose.runOnIdle {
            height = 940.dp
            state =
                state.copy(
                    messages =
                        listOf(
                            ConversationMessage("keyboard", "오늘 하루도 수고했어요.\n어떤 일이 있었는지 들려줄래요?", false),
                        ),
                )
            draft = TextFieldValue("오늘 하루가 조금 힘들었어")
        }
        capture("keyboard-input-aaos")
        val resized = compose.onNodeWithTag("chat-panel").fetchSemanticsNode().boundsInRoot
        val composer = compose.onNodeWithTag("chat-composer").fetchSemanticsNode().boundsInRoot
        val avatar = compose.onNodeWithTag("chat-avatar").fetchSemanticsNode().boundsInRoot
        assertEquals(882f, resized.height, 1f)
        assertEquals(854f, composer.left, 1f)
        assertEquals(805f, composer.top, 1f)
        assertEquals(87f, composer.height, 1f)
        assertEquals(192f, avatar.left, 1f)
        assertEquals(304f, avatar.top, 1f)
        assertEquals(440f, avatar.width, 1f)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun tallerSystemNavigationShrinksCompanionBeforeTextOverlaps() {
        height = 1120.dp
        show()
        capture("navigation-resized")
        val companion = compose.onNodeWithTag("chat-companion").fetchSemanticsNode().boundsInRoot
        val panel = compose.onNodeWithTag("chat-panel").fetchSemanticsNode().boundsInRoot
        val avatar = compose.onNodeWithTag("chat-avatar").fetchSemanticsNode().boundsInRoot
        assertEquals(900f, companion.height, 1f)
        assertEquals(1096f, panel.bottom, 1f)
        assertEquals(440f, avatar.width, 1f)
        compose.onNodeWithTag("chat-composer").assertIsDisplayed()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun visibleHistoryKeepsNewConversationReachableAtReferenceSize() {
        assertVisibleHistoryCanBeReset(fontScale = 1f)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun visibleHistoryKeepsNewConversationReachableWithEnlargedText() {
        assertVisibleHistoryCanBeReset(fontScale = 1.6f)
    }

    private fun assertVisibleHistoryCanBeReset(fontScale: Float) {
        state =
            state.copy(
                messages =
                    listOf(
                        ConversationMessage("1", "previous exchange", true),
                        ConversationMessage("2", "previous reply", false),
                    ),
            )
        show(fontScale = fontScale)
        capture(if (fontScale == 1f) "visible-history" else "visible-history-enlarged")
        compose.onNodeWithText("previous exchange").assertExists()
        compose.onNodeWithText("previous reply").assertExists()
        compose.onNodeWithTag("chat-new-action").assertIsDisplayed().performClick()
        compose.onNodeWithTag("chat-new-action").assertDoesNotExist()
        compose.onNodeWithText("previous exchange").assertDoesNotExist()
        compose.onNodeWithText("previous reply").assertDoesNotExist()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun voiceListeningAndReviewMatchTheReferenceTypographyAndButtonBounds() {
        state =
            state.copy(
                messages = emptyList(),
                voice =
                    VoiceInputState(
                        true,
                        VoiceInputPhase.LISTENING,
                        levels = referenceVoiceLevels,
                    ),
            )
        draft = TextFieldValue("original draft")
        show()
        capture("voice-listening")
        val bar = compose.onNodeWithTag("chat-voice-control").fetchSemanticsNode().boundsInRoot
        assertEquals(853.5f, bar.left, 1f)
        assertEquals(1020.5f, bar.top, 1f)
        assertEquals(1577f, bar.width, 1f)
        assertEquals(87f, bar.height, 1f)
        val cancel =
            compose
                .onNodeWithTag(
                    "chat-voice-cancel-visual",
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                .boundsInRoot
        val stop =
            compose
                .onNodeWithTag(
                    "chat-voice-stop-visual",
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                .boundsInRoot
        assertEquals(872.87f, cancel.left, 1f)
        assertEquals(1029.1f, cancel.top, 1f)
        assertEquals(70f, cancel.width, 1f)
        assertEquals(69.8f, cancel.height, 1f)
        assertEquals(2250f, stop.left, 1f)
        assertEquals(1028f, stop.top, 1f)
        assertEquals(72f, stop.width, 1f)
        compose.onNodeWithTag("chat-send").assertIsNotEnabled()
        compose.onNodeWithTag("chat-new-action").assertDoesNotExist()
        compose.onNodeWithText("듣고 있어요", substring = true).assertDoesNotExist()
        compose
            .onNodeWithText(
                "대화와 확인된 이름·시간·차량 상태 신호는 GitHub Copilot에 전달돼요. 디버거 사용 중에는 테스트 값이 전달돼요. 답변은 부정확할 수 있어요.",
            ).assertIsDisplayed()
        compose.onNodeWithContentDescription("녹음 마치고 내용 확인").performClick()
        compose.runOnIdle {
            assertEquals(1, voiceStops)
            assertEquals(0, sends)
            assertEquals("original draft", draft.text)
            state = state.copy(voice = state.voice.copy(phase = VoiceInputPhase.REVIEW))
            draft = TextFieldValue("모비는 뭐가 좋았어?", TextRange("모비는 뭐가 좋았어?".length))
        }
        compose.runOnIdle { state = state.copy(voice = state.voice.copy(problem = VoiceInputProblem.NO_MATCH)) }
        capture("voice-review")
        compose.onNodeWithTag("chat-voice-hint").assertDoesNotExist()
        compose
            .onNodeWithText(
                "대화와 확인된 이름·시간·차량 상태 신호는 GitHub Copilot에 전달돼요. 디버거 사용 중에는 테스트 값이 전달돼요. 답변은 부정확할 수 있어요.",
            ).assertIsDisplayed()
        compose.onNodeWithTag("chat-input").assertIsDisplayed()
        compose.onNodeWithContentDescription("음성으로 입력").assertIsDisplayed()
        val composer = compose.onNodeWithTag("chat-composer").fetchSemanticsNode().boundsInRoot
        val send = compose.onNodeWithTag("chat-send-visual", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(853.5f, composer.left, 1f)
        assertEquals(1020.5f, composer.top, 1f)
        assertEquals(1577f, composer.width, 1f)
        assertEquals(87f, composer.height, 1f)
        assertEquals(2343.1f, send.left, 1f)
        assertEquals(1028f, send.top, 1f)
        assertEquals(68.53f, send.width, 1f)
        assertEquals(72f, send.height, 1f)
        compose.onNodeWithTag("chat-input").performTextInput(" 수정")
        compose.runOnIdle { assertEquals(0, sends) }
        compose.onNodeWithTag("chat-send").performClick()
        compose.runOnIdle {
            assertEquals(1, sends)
            assertEquals("모비는 뭐가 좋았어? 수정", state.messages.single().text)
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun voiceControlsRetainMinimumTouchBoundsAtAaosDensityAndEnlargedText() {
        state = state.copy(messages = emptyList(), voice = VoiceInputState(true, VoiceInputPhase.LISTENING))
        show(density = 10f / 7f)
        capture("voice-listening-aaos")
        compose.onNodeWithContentDescription("음성 입력 취소").assertHeightIsAtLeast(76.dp)
        compose.onNodeWithContentDescription("녹음 마치고 내용 확인").assertHeightIsAtLeast(76.dp)
        val bar = compose.onNodeWithTag("chat-voice-control").fetchSemanticsNode().boundsInRoot
        assertEquals(853.5f, bar.left, 1f)
        assertEquals(1020.5f, bar.top, 1f)
        compose.onNodeWithContentDescription("음성 입력 취소").performClick()
        compose.runOnIdle {
            assertEquals(1, voiceCancellations)
            assertEquals(0, sends)
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun enlargedVoiceReviewIsEditableAndUnavailableReadinessBlocksSend() {
        draft = TextFieldValue("모비는 뭐가 좋았어?")
        state =
            state.copy(
                connection = ConversationConnection.UNAVAILABLE,
                voice = VoiceInputState(true, VoiceInputPhase.REVIEW),
            )
        show(fontScale = 1.6f, density = 10f / 7f)
        capture("voice-review-enlarged")
        compose.onNodeWithTag("chat-input").assertIsDisplayed().performTextInput(" 수정")
        compose.onNodeWithTag("chat-send").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, sends) }
        compose.runOnIdle { allowed = false }
        compose.onNodeWithTag("chat-parking-dialog").assertIsDisplayed()
        compose.onNodeWithTag("chat-input").assertDoesNotExist()
        compose.runOnIdle { assertTrue(draft.text.contains("수정")) }
    }

    @Test
    fun voiceStartAndCancellationDoNotSubmitWithoutOwnerDraftChange() {
        state = state.copy(voice = VoiceInputState(available = true))
        draft = TextFieldValue("original")
        show()
        compose.onNodeWithContentDescription("음성으로 입력").performClick()
        compose.onNodeWithTag("chat-voice-control").assertIsDisplayed()
        compose.onNodeWithContentDescription("음성 입력 취소").performClick()
        compose.onNodeWithTag("chat-input").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, voiceStarts)
            assertEquals(1, voiceCancellations)
            assertEquals("original", draft.text)
            assertEquals(0, sends)
        }
    }

    @Test
    fun networkLossDuringRecordingOpensPopupAndReleasesMicrophone() {
        state = state.copy(voice = VoiceInputState(true, VoiceInputPhase.LISTENING))
        show()
        compose.runOnIdle {
            state =
                state.copy(
                    connection = ConversationConnection.UNAVAILABLE,
                    connectionProblem = ConversationProblem.NETWORK,
                )
        }
        compose.onNodeWithTag("chat-network-dialog").assertIsDisplayed()
        compose.onNodeWithContentDescription("녹음 마치고 내용 확인").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, voiceCancellations)
            assertEquals(0, sends)
            allowed = false
        }
        compose.onNodeWithTag("chat-parking-dialog").assertIsDisplayed()
    }

    private fun show(
        fontScale: Float = 1f,
        density: Float = 1f,
    ) {
        compose.setContent {
            val current = LocalView.current
            SideEffect { view = current }
            CompositionLocalProvider(
                LocalMobiMonMotionEnabled provides false,
                LocalDensity provides Density(density, fontScale),
            ) {
                MobiMonTheme {
                    ConversationScreen(
                        state,
                        draft,
                        { draft = it },
                        { text ->
                            sends++
                            state =
                                state.copy(
                                    messages = listOf(ConversationMessage("sent", text, true)),
                                    replyPending = true,
                                )
                            draft =
                                TextFieldValue()
                        },
                        {
                            cancellations++
                            state = state.copy(replyPending = false)
                        },
                        {},
                        { connectionOpens++ },
                        Modifier.height(height / density),
                        interactionAllowed = allowed,
                        onReturnHome = { homeReturns++ },
                        onRetry = { retries++ },
                        onRecheckConnection = {
                            checks++
                            state =
                                state.copy(
                                    connection = ConversationConnection.CHECKING,
                                    connectionProblem = null,
                                    connectionRetrying = true,
                                )
                        },
                        onDismissFailure = {
                            state.messages.lastOrNull()?.takeIf { state.failed && it.fromUser }?.let {
                                draft = TextFieldValue(it.text, TextRange(it.text.length))
                            }
                            state = state.copy(messages = state.messages.dropLast(1), failed = false)
                        },
                        onNewConversation = {
                            state =
                                state.copy(
                                    messages = emptyList(),
                                )
                        },
                        onStartVoice = {
                            voiceStarts++
                            state = state.copy(voice = state.voice.copy(phase = VoiceInputPhase.LISTENING))
                        },
                        onStopVoice = {
                            voiceStops++
                            state = state.copy(voice = state.voice.copy(phase = VoiceInputPhase.STOPPING))
                        },
                        onCancelVoice = {
                            if (state.voice.capturing) voiceCancellations++
                            state = state.copy(voice = state.voice.copy(phase = VoiceInputPhase.IDLE))
                        },
                        onFinishVoiceReview = {
                            state =
                                state.copy(voice = state.voice.copy(phase = VoiceInputPhase.IDLE))
                        },
                    )
                }
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("build/reports/conversation-ui").apply { mkdirs() }.resolve("$name.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            bitmap.recycle()
        }
    }

    private fun assertPaintBounds(
        bitmap: Bitmap,
        xRange: IntRange,
        yRange: IntRange,
        color: Int,
        expected: Rect,
    ) {
        val actual = Rect(Int.MAX_VALUE, Int.MAX_VALUE, 0, 0)
        for (y in yRange) {
            for (x in xRange) {
                val pixel = bitmap.getPixel(x, y)
                if (abs(Color.red(pixel) - Color.red(color)) < 25 &&
                    abs(Color.green(pixel) - Color.green(color)) < 25 &&
                    abs(Color.blue(pixel) - Color.blue(color)) < 25
                ) {
                    actual.left = minOf(actual.left, x)
                    actual.top = minOf(actual.top, y)
                    actual.right = maxOf(actual.right, x + 1)
                    actual.bottom = maxOf(actual.bottom, y + 1)
                }
            }
        }
        assertTrue("No matching pixels in $xRange x $yRange", actual.left != Int.MAX_VALUE)
        assertTrue("Expected $expected, found $actual", abs(actual.left - expected.left) <= 1)
        assertTrue("Expected $expected, found $actual", abs(actual.top - expected.top) <= 1)
        assertTrue("Expected $expected, found $actual", abs(actual.right - expected.right) <= 1)
        assertTrue("Expected $expected, found $actual", abs(actual.bottom - expected.bottom) <= 1)
    }

    private val messages =
        listOf(
            ConversationMessage("user", "오늘은 조금 피곤한 하루였어.", true),
            ConversationMessage("reply", "오늘 하루도 수고했어요.\n지금은 잠깐 쉬어 가도 괜찮아요.\n어떤 일이 있었는지 들려줄래요?", false),
        )

    private val referenceVoiceLevels =
        listOf(
            8f,
            9f,
            11f,
            14f,
            18f,
            22f,
            25f,
            28f,
            29f,
            28f,
            25f,
            20f,
            16f,
            12f,
            9f,
            8f,
            10f,
            14f,
            20f,
            27f,
            34f,
            40f,
            44f,
            46f,
            44f,
            39f,
            32f,
            25f,
            18f,
            12f,
            9f,
            8f,
            11f,
            17f,
            24f,
            32f,
            39f,
            44f,
            46f,
            46f,
            42f,
            36f,
            29f,
            22f,
            15f,
            10f,
            8f,
            9f,
            11f,
            16f,
            21f,
            26f,
            29f,
            31f,
            31f,
            29f,
            25f,
            21f,
            16f,
            13f,
            10f,
            8f,
            8f,
            8f,
        ).map {
            (
                it -
                    8f
            ) /
                38f
        }

    private val referenceMessages =
        listOf(
            ConversationMessage("first-user", "오늘은 조금 피곤한 하루였어.", true),
            ConversationMessage("first-reply", "오늘 하루도 수고했어요.\n잠깐 쉬면서 편하게 이야기해 볼까요?", false),
            ConversationMessage("second-user", "응, 기분 좋아지는 얘기 해줘.", true),
            ConversationMessage("second-reply", "좋아요. 오늘 발견한 작은 행복부터 나눠 볼까요?", false),
        )
}
