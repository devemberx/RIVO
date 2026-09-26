package com.monsters.mobimon.feature.auth.preview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.sp
import com.monsters.mobimon.core.domain.ConversationProblem
import com.monsters.mobimon.core.ui.LocalMobiMonMotionEnabled
import com.monsters.mobimon.core.ui.MobiMonColors
import com.monsters.mobimon.core.ui.MobiMonTheme
import com.monsters.mobimon.feature.auth.ConversationConnection
import com.monsters.mobimon.feature.auth.ConversationMessage
import com.monsters.mobimon.feature.auth.ConversationScreen
import com.monsters.mobimon.feature.auth.ConversationUiState
import com.monsters.mobimon.feature.auth.VoiceInputPhase
import com.monsters.mobimon.feature.auth.VoiceInputState

/** Debug-only visual rehearsal. No repositories, credentials, provider or simulated approval. */
class ConversationPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sample = intent.getStringExtra("state")
        val sampleProblem =
            when (sample) {
                "network-failed" -> ConversationProblem.NETWORK
                "usage-failed" -> ConversationProblem.USAGE
                "access-failed" -> ConversationProblem.ACCESS
                "account-failed" -> ConversationProblem.ACCOUNT
                "timeout-failed" -> ConversationProblem.TIMEOUT
                else -> null
            }
        val failedSamples =
            setOf("failed", "network-failed", "usage-failed", "access-failed", "account-failed", "timeout-failed")
        val messages =
            listOf(
                ConversationMessage("sample-user", "오늘은 조금 피곤한 하루였어.", true),
                ConversationMessage("sample-reply", "오늘 하루도 수고했어요.\n지금은 잠깐 쉬어 가도 괜찮아요.\n어떤 일이 있었는지 들려줄래요?", false),
            )
        val voiceSample = sample == "voice-listening" || sample == "voice-review"
        val voiceMessages =
            listOf(
                ConversationMessage("first-user", "오늘은 조금 피곤한 하루였어.", true),
                ConversationMessage("first-reply", "오늘 하루도 수고했어요.\n잠깐 쉬면서 편하게 이야기해 볼까요?", false),
                ConversationMessage("second-user", "응, 기분 좋아지는 얘기 해줘.", true),
                ConversationMessage("second-reply", "좋아요. 오늘 발견한 작은 행복부터 나눠 볼까요?", false),
            )
        val voiceLevels =
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
        setContent {
            var state by remember {
                mutableStateOf(
                    ConversationUiState(
                        connection =
                            when (sample) {
                                "network-failed", "usage-failed", "access-failed", "account-failed", "timeout-failed" ->
                                    ConversationConnection.UNAVAILABLE
                                "checking" -> ConversationConnection.CHECKING
                                else -> ConversationConnection.READY
                            },
                        connectionRetrying = sample == "checking",
                        voice =
                            VoiceInputState(
                                available = voiceSample,
                                phase =
                                    if (sample ==
                                        "voice-listening"
                                    ) {
                                        VoiceInputPhase.LISTENING
                                    } else if (sample ==
                                        "voice-review"
                                    ) {
                                        VoiceInputPhase.REVIEW
                                    } else {
                                        VoiceInputPhase.IDLE
                                    },
                                elapsedSeconds = 8,
                                levels = voiceLevels,
                            ),
                        messages =
                            when (sample) {
                                "voice-listening", "voice-review" -> voiceMessages
                                "messages" -> messages
                                "keyboard" ->
                                    listOf(
                                        ConversationMessage(
                                            "sample-keyboard",
                                            "오늘 하루도 수고했어요.\n어떤 일이 있었는지 들려줄래요?",
                                            false,
                                        ),
                                    )
                                "pending" -> messages.take(1)
                                in failedSamples ->
                                    listOf(
                                        messages.first(),
                                        ConversationMessage(
                                            "sample-reply",
                                            "오늘 하루도 수고했어요.\n잠깐 쉬면서 편하게 이야기해 볼까요?",
                                            false,
                                        ),
                                        ConversationMessage("sample-next", "응, 기분 좋아지는 얘기 해줘.", true),
                                        ConversationMessage(
                                            "sample-next-reply",
                                            "좋아요. 오늘 발견한 작은 행복부터 나눠 볼까요?",
                                            false,
                                        ),
                                        ConversationMessage("sample-failed", "모비는 뭐가 좋아?", true),
                                    )
                                else -> emptyList()
                            },
                        replyPending = sample == "pending",
                        failed = sample == "failed" || sampleProblem != null,
                        problem = sampleProblem,
                        connectionProblem = sampleProblem,
                    ),
                )
            }
            var draft by remember {
                mutableStateOf(
                    TextFieldValue(
                        when (sample) {
                            "voice-review" -> "모비는 뭐가 좋았어?"
                            "keyboard" -> "오늘 하루가 조금 힘들었어"
                            in failedSamples ->
                                "모비는 뭐가 좋아?"
                            else -> ""
                        },
                        selection = TextRange(if (sample == "voice-review") "모비는 뭐가 좋았어?".length else 0),
                    ),
                )
            }
            CompositionLocalProvider(LocalMobiMonMotionEnabled provides (sample != "reduced-motion")) {
                MobiMonTheme {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MobiMonColors.background)
                            .safeDrawingPadding()
                            .imePadding(),
                    ) {
                        ConversationScreen(
                            state,
                            draft,
                            { draft = it },
                            { text ->
                                state =
                                    state.copy(
                                        messages =
                                            state.messages +
                                                ConversationMessage("sample-${state.messages.size}", text, true),
                                        replyPending = true,
                                    )
                                draft = TextFieldValue()
                            },
                            { state = state.copy(replyPending = false) },
                            ::finish,
                            {},
                            Modifier.fillMaxSize(),
                            interactionAllowed = true,
                            onRetry = { state = state.copy(failed = false) },
                            onRecheckConnection = {
                                state =
                                    state.copy(
                                        connection = ConversationConnection.READY,
                                        connectionProblem = null,
                                        connectionRetrying = false,
                                    )
                            },
                            onDismissFailure = {
                                state = state.copy(messages = state.messages.dropLast(1), failed = false)
                            },
                            onStartVoice = {
                                state =
                                    state.copy(voice = state.voice.copy(phase = VoiceInputPhase.LISTENING))
                            },
                            onStopVoice = {
                                state =
                                    state.copy(
                                        voice = state.voice.copy(phase = VoiceInputPhase.REVIEW),
                                    )
                            },
                            onCancelVoice = {
                                state =
                                    state.copy(
                                        voice = state.voice.copy(phase = VoiceInputPhase.IDLE),
                                    )
                            },
                            onFinishVoiceReview = {
                                state =
                                    state.copy(voice = state.voice.copy(phase = VoiceInputPhase.IDLE))
                            },
                            onNewConversation = {
                                state = state.copy(messages = emptyList(), failed = false, replyPending = false)
                                draft = TextFieldValue()
                            },
                        )
                        Text(
                            "DEBUG UI preview · Sample conversation · No provider connection",
                            Modifier.align(Alignment.BottomStart),
                            fontSize = 12.sp,
                            color = MobiMonColors.warning,
                        )
                    }
                }
            }
        }
    }
}
